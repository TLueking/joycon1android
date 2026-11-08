// evdev relay process: runs as the shell user (u:r:shell:s0), which is in the input group.
// Finds Switch 1 controllers bound by hid-nintendo, grabs them so Android stops delivering
// their input, and streams their events on stdout. Exits when stdin closes.
// Protocol: docs/switch1.md#evdev-relay

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define MAX_DEVICES 16
#define NINTENDO_VENDOR 0x057e
#define RESCAN_INTERVAL_MS 2000
#define BITS_PER_LONG (sizeof(long) * 8)
#define BIT_COUNT(n) (((n) + BITS_PER_LONG - 1) / BITS_PER_LONG)

struct device {
    int fd;
    int number;
};

static struct device devices[MAX_DEVICES];
static int device_count;

static int is_supported_product(int product) {
    return product == 0x2006 || product == 0x2007 || product == 0x2009;
}

static int test_bit(const unsigned long *bits, int bit) {
    return (bits[bit / BITS_PER_LONG] >> (bit % BITS_PER_LONG)) & 1;
}

static int is_tracked(int number) {
    for (int i = 0; i < device_count; i++) {
        if (devices[i].number == number) return 1;
    }
    return 0;
}

static void strip_to_token(char *text) {
    size_t length = strcspn(text, "\r\n");
    text[length] = '\0';
    for (char *c = text; *c; c++) {
        if (*c == ' ' || *c == '\t') *c = '_';
    }
}

static int read_file(const char *path, char *out, size_t size) {
    FILE *file = fopen(path, "r");
    if (!file) return 0;
    size_t length = fread(out, 1, size - 1, file);
    fclose(file);
    out[length] = '\0';
    return length > 0;
}

// Older hid-nintendo leaves uniq empty; the HID parent's carries the address where sysfs is readable.
static void read_address(int fd, int number, char *out, size_t size) {
    char path[128];
    char text[1024] = {0};
    if (ioctl(fd, EVIOCGUNIQ(sizeof(text) - 1), text) >= 0) {
        strip_to_token(text);
        if (text[0]) {
            snprintf(out, size, "%s", text);
            return;
        }
    }
    snprintf(path, sizeof(path), "/sys/class/input/event%d/device/uniq", number);
    if (read_file(path, text, sizeof(text))) {
        strip_to_token(text);
        if (text[0]) {
            snprintf(out, size, "%s", text);
            return;
        }
    }
    snprintf(path, sizeof(path), "/sys/class/input/event%d/device/device/uevent", number);
    if (read_file(path, text, sizeof(text))) {
        char *uniq = strstr(text, "HID_UNIQ=");
        if (uniq) {
            uniq += strlen("HID_UNIQ=");
            strip_to_token(uniq);
            if (uniq[0]) {
                snprintf(out, size, "%s", uniq);
                return;
            }
        }
    }
    snprintf(out, size, "-");
}

static void emit_state(const struct device *device) {
    unsigned long keys[BIT_COUNT(KEY_MAX + 1)] = {0};
    unsigned long abs_bits[BIT_COUNT(ABS_MAX + 1)] = {0};
    if (ioctl(device->fd, EVIOCGKEY(sizeof(keys)), keys) >= 0) {
        for (int code = 0; code <= KEY_MAX; code++) {
            if (test_bit(keys, code)) printf("E %d %d %d 1\n", device->number, EV_KEY, code);
        }
    }
    if (ioctl(device->fd, EVIOCGBIT(EV_ABS, sizeof(abs_bits)), abs_bits) >= 0) {
        for (int code = 0; code <= ABS_MAX; code++) {
            struct input_absinfo info;
            if (test_bit(abs_bits, code) && ioctl(device->fd, EVIOCGABS(code), &info) >= 0) {
                printf("E %d %d %d %d\n", device->number, EV_ABS, code, info.value);
            }
        }
    }
    printf("S %d\n", device->number);
}

static void try_add(int number) {
    if (device_count >= MAX_DEVICES || is_tracked(number)) return;

    char path[64];
    snprintf(path, sizeof(path), "/dev/input/event%d", number);
    int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) return;

    struct input_id id;
    char name[128] = {0};
    if (ioctl(fd, EVIOCGID, &id) < 0 || id.vendor != NINTENDO_VENDOR || !is_supported_product(id.product) ||
        ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name) < 0 || strstr(name, "IMU")) {
        close(fd);
        return;
    }
    if (ioctl(fd, EVIOCGRAB, (void *)1) < 0) {
        fprintf(stderr, "evdev_relay: grab %s failed: %s\n", path, strerror(errno));
    }

    char address[64];
    read_address(fd, number, address, sizeof(address));
    strip_to_token(name);

    struct device *device = &devices[device_count++];
    device->fd = fd;
    device->number = number;
    printf("A %d %04x %s %s\n", number, id.product, address, name);
    emit_state(device);
}

static void scan(void) {
    DIR *dir = opendir("/dev/input");
    if (!dir) return;
    struct dirent *entry;
    while ((entry = readdir(dir))) {
        int number;
        if (sscanf(entry->d_name, "event%d", &number) == 1) try_add(number);
    }
    closedir(dir);
}

static void remove_at(int index) {
    close(devices[index].fd);
    printf("R %d\n", devices[index].number);
    devices[index] = devices[--device_count];
}

// Returns 0 once the device is gone.
static int drain(const struct device *device) {
    struct input_event events[64];
    for (;;) {
        ssize_t bytes = read(device->fd, events, sizeof(events));
        if (bytes < 0) return errno == EAGAIN || errno == EINTR;
        if (bytes == 0) return 0;
        size_t count = (size_t)bytes / sizeof(events[0]);
        for (size_t i = 0; i < count; i++) {
            const struct input_event *event = &events[i];
            if (event->type == EV_KEY || event->type == EV_ABS) {
                printf("E %d %d %d %d\n", device->number, event->type, event->code, event->value);
            } else if (event->type == EV_SYN && event->code == SYN_REPORT) {
                printf("S %d\n", device->number);
            } else if (event->type == EV_SYN && event->code == SYN_DROPPED) {
                emit_state(device);
            }
        }
    }
}

static void drain_inotify(int fd) {
    char buffer[4096];
    while (read(fd, buffer, sizeof(buffer)) > 0) {
    }
}

int main(void) {
    int watch = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (watch >= 0) inotify_add_watch(watch, "/dev/input", IN_CREATE | IN_ATTRIB);

    printf("OK\n");
    scan();
    fflush(stdout);

    for (;;) {
        struct pollfd fds[MAX_DEVICES + 2];
        fds[0] = (struct pollfd){.fd = STDIN_FILENO, .events = POLLIN};
        fds[1] = (struct pollfd){.fd = watch, .events = POLLIN};
        for (int i = 0; i < device_count; i++) {
            fds[i + 2] = (struct pollfd){.fd = devices[i].fd, .events = POLLIN};
        }
        int polled_devices = device_count;

        int ready = poll(fds, (nfds_t)(polled_devices + 2), RESCAN_INTERVAL_MS);
        if (ready < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (fds[0].revents) {
            char discard[64];
            if (read(STDIN_FILENO, discard, sizeof(discard)) <= 0) break;
        }

        // Backwards, so remove_at's swap doesn't skip an unpolled slot.
        for (int i = polled_devices - 1; i >= 0; i--) {
            short revents = fds[i + 2].revents;
            if (!revents) continue;
            if ((revents & (POLLERR | POLLHUP | POLLNVAL)) || !drain(&devices[i])) remove_at(i);
        }

        if (ready == 0 || (watch >= 0 && fds[1].revents)) {
            if (watch >= 0) drain_inotify(watch);
            scan();
        }
        fflush(stdout);
    }

    for (int i = device_count - 1; i >= 0; i--) remove_at(i);
    fflush(stdout);
    return 0;
}

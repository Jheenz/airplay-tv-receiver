/* Host regression test: compile with android_dnssd_shim.c and UxPlay/lib/utils.c. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "android_dnssd_shim.h"

static void check_record(dnssd_t *dns, int raop) {
    int length = -1;
    const char *wire = raop ? dnssd_get_raop_txt(dns, &length)
                            : dnssd_get_airplay_txt(dns, &length);
    assert(wire && length > 0);
    int count = raop ? android_dnssd_get_raop_txt_count(dns)
                     : android_dnssd_get_airplay_txt_count(dns);
    int offset = 0;
    for (int i = 0; i < count; i++) {
        const char *key = raop ? android_dnssd_get_raop_txt_key(dns, i)
                               : android_dnssd_get_airplay_txt_key(dns, i);
        const char *val = raop ? android_dnssd_get_raop_txt_val(dns, i)
                               : android_dnssd_get_airplay_txt_val(dns, i);
        assert(offset < length);
        unsigned int size = (unsigned char) wire[offset++];
        assert(size > 0 && offset + size <= length);
        const char *equals = memchr(wire + offset, '=', size);
        assert(equals);
        size_t key_len = equals - (wire + offset);
        size_t val_len = size - key_len - 1;
        assert(key_len == strlen(key) && !memcmp(wire + offset, key, key_len));
        assert(val_len == strlen(val) && !memcmp(equals + 1, val, val_len));
        offset += size;
    }
    assert(offset == length);
}

int main(void) {
    const char hw[] = {0, 1, 2, 3, 4, 5};
    for (int pin = 0; pin <= 3; pin++) {
        int error = -1;
        dnssd_t *dns = dnssd_init("Test receiver", 13, hw, sizeof(hw), &error, pin);
        assert(dns && error == DNSSD_ERROR_NOERROR);
        dnssd_set_pk(dns, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        for (int alac = 0; alac <= 1; alac++) {
            android_dnssd_set_codecs(dns, alac, 1);
            dnssd_set_airplay_features(dns, 42, alac);
            assert(dnssd_register_raop(dns, 7000) == 0);
            assert(dnssd_register_airplay(dns, 7000) == 0);
            check_record(dns, 1);
            check_record(dns, 0);
            int length;
            const char *raop = dnssd_get_raop_txt(dns, &length);
            char saved[8192];
            assert(length <= sizeof(saved));
            memcpy(saved, raop, length);
            assert(dnssd_get_airplay_txt(dns, NULL));
            assert(!memcmp(saved, raop, length));
        }
        dnssd_destroy(dns);
    }
    puts("DNS-SD /info records match discovery records: PASS");
}

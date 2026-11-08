// BTstack configuration for the Pico W keyboard injector.
// BLE-only (no Bluetooth Classic) to keep flash/RAM usage small.
// Based on pico-examples bluetooth config (ENABLE_* prefixed macros).
#ifndef _PICO_BTSTACK_CONFIG_H
#define _PICO_BTSTACK_CONFIG_H

// BTstack features that can be enabled
#define ENABLE_LOG_INFO
#define ENABLE_LOG_ERROR
#define ENABLE_PRINTF_HEXDUMP

#ifdef ENABLE_BLE
#define ENABLE_L2CAP_LE_CREDIT_BASED_FLOW_CONTROL_MODE
#define ENABLE_LE_CENTRAL
#define ENABLE_LE_DATA_LENGTH_EXTENSION
#define ENABLE_LE_PERIPHERAL
#define ENABLE_LE_PRIVACY_ADDRESS_RESOLUTION
#define ENABLE_LE_SECURE_CONNECTIONS
#define ENABLE_LE_ENHANCED_CONNECTION_COMPLETE_EVENT
#endif

// BTstack configuration: buffers, sizes
#define HCI_ACL_PAYLOAD_SIZE 255
#define HCI_OUTGOING_PRE_BUFFER_SIZE 4
#define HCI_ACL_CHUNK_SIZE_ALIGNMENT 4
#define MAX_NR_BNEP_CHANNELS 0
#define MAX_NR_BNEP_SERVICES 0

// LE only: keep connection pool small (single Pico W <- phone link).
#define MAX_NR_HCI_CONNECTIONS 1
#define MAX_NR_SM_LOOKUP_ENTRIES 3
#define MAX_NR_SM_MASTER_KEY_ENTRIES 3
#define MAX_NR_LE_DEVICE_DB_ENTRIES 1
// LE device DB persisted via TLV on flash (single paired host)
#define NVM_NUM_DEVICE_DB_ENTRIES 1
#define NVM_NUM_LINK_KEYS 1
#define MAX_NR_GATT_CLIENTS 0
#define MAX_NR_L2CAP_CHANNELS  1
#define MAX_NR_L2CAP_SERVICES  1
#define MAX_NR_ATT_SERVER_HANDLES 4
#define MAX_ATT_MTU 256

// GATT database size: fixed ATT DB (no malloc).
#define MAX_ATT_DB_SIZE 512

// HCI Controller to Host Flow Control to avoid cyw43 shared bus overrun
#define ENABLE_HCI_CONTROLLER_TO_HOST_FLOW_CONTROL
#define HCI_HOST_ACL_PACKET_LEN 1024
#define HCI_HOST_ACL_PACKET_NUM 3
#define HCI_HOST_SCO_PACKET_LEN 120
#define HCI_HOST_SCO_PACKET_NUM 3

// Limit number of ACL/SCO buffers used by the stack to avoid cyw43 shared bus overrun
#define MAX_NR_CONTROLLER_ACL_BUFFERS 3
#define MAX_NR_CONTROLLER_SCO_PACKETS 3

// BTstack HAL configuration
#define HAVE_EMBEDDED_TIME_MS
#define HAVE_BTSTACK_STDIN
#define HAVE_POSIX_FILE_IO
#define HAVE_BTSTACK_MEMORY

// map btstack_assert onto Pico SDK assert()
#define HAVE_ASSERT

#define ENABLE_SOFTWARE_AES128
#define ENABLE_MICRO_ECC_FOR_LE_SECURE_CONNECTIONS

// char * vs const char *
#define CHAR_CONST const

#endif // _PICO_BTSTACK_CONFIG_H

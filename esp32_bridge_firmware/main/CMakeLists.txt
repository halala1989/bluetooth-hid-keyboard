idf_component_register(
    SRCS "bridge.c"
    INCLUDE_DIRS "."
    PRIV_REQUIRES nvs_flash bt
    )
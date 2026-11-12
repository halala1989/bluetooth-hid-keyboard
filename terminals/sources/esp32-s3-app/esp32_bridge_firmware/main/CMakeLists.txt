idf_component_register(
    SRCS "bridge.c" "gbk_table.c"
    INCLUDE_DIRS "."
    PRIV_REQUIRES nvs_flash bt driver
    )

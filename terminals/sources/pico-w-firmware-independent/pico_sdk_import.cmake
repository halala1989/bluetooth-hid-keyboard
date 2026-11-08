# This is a copy of <PICO_SDK_PATH>/external/pico_sdk_import.cmake
# It can be dropped into an external project to help locate the Pico SDK.
# It should be include()'d prior to project().

if (DEFINED ENV{PICO_SDK_PATH} AND (NOT PICO_SDK_PATH))
    set(PICO_SDK_PATH $ENV{PICO_SDK_PATH})
    message("Using PICO_SDK_PATH from environment ('${PICO_SDK_PATH}')")
endif ()

if (DEFINED ENV{PICO_SDK_FETCH_FROM_GIT} AND (NOT PICO_SDK_FETCH_FROM_GIT))
    set(PICO_SDK_FETCH_FROM_GIT $ENV{PICO_SDK_FETCH_FROM_GIT})
    message("Using PICO_SDK_FETCH_FROM_GIT from environment ('${PICO_SDK_FETCH_FROM_GIT}')")
endif ()

if (DEFINED ENV{PICO_SDK_FETCH_FROM_GIT_PATH} AND (NOT PICO_SDK_FETCH_FROM_GIT_PATH))
    set(PICO_SDK_FETCH_FROM_GIT_PATH $ENV{PICO_SDK_FETCH_FROM_GIT_PATH})
    message("Using PICO_SDK_FETCH_FROM_GIT_PATH from environment ('${PICO_SDK_FETCH_FROM_GIT_PATH}')")
endif ()

if (DEFINED PICO_SDK_PATH)
    set(PICO_SDK_PATH "${PICO_SDK_PATH}")
else ()
    if (NOT PICO_SDK_FETCH_FROM_GIT)
        # Default to ~/.pico-sdk/sdk/<release>
        set(PICO_SDK_PATH "$ENV{HOME}/.pico-sdk/sdk/2.1.0")
        if (NOT EXISTS ${PICO_SDK_PATH})
            message(FATAL_ERROR
                "PICO_SDK_PATH not defined and default '${PICO_SDK_PATH}' does not exist.\n"
                "Set PICO_SDK_PATH (or export it as an environment variable), or enable "
                "PICO_SDK_FETCH_FROM_GIT.")
        endif ()
    endif ()
    if (PICO_SDK_FETCH_FROM_GIT)
        include(FetchContent)
        set(FETCHCONTENT_BASE_DIR_SAVE ${FETCHCONTENT_BASE_DIR})
        if (NOT PICO_SDK_FETCH_FROM_GIT_PATH)
            set(PICO_SDK_FETCH_FROM_GIT_PATH ${FETCHCONTENT_BASE_DIR_SAVE}/pico-sdk)
        else ()
            get_filename_component(PICO_SDK_FETCH_FROM_GIT_PATH "${PICO_SDK_FETCH_FROM_GIT_PATH}" REALPATH BASE_DIR "${CMAKE_BINARY_DIR}")
        endif ()
        set(FETCHCONTENT_BASE_DIR "${FETCHCONTENT_BASE_DIR_SAVE}/${PICO_SDK_FETCH_FROM_GIT_PATH}")
        message("Using PICO_SDK_FETCH_FROM_GIT_PATH from environment ('${PICO_SDK_FETCH_FROM_GIT_PATH}')")
        FetchContent_Declare(
            pico_sdk
            GIT_REPOSITORY https://github.com/raspberrypi/pico-sdk
            GIT_TAG 2.1.0
        )
        if (NOT pico_sdk)
            message("Downloading Raspberry Pi Pico SDK")
            FetchContent_Populate(pico_sdk)
            set(PICO_SDK_PATH ${pico_sdk_SOURCE_DIR})
        endif ()
        set(FETCHCONTENT_BASE_DIR ${FETCHCONTENT_BASE_DIR_SAVE})
    endif ()
endif ()

set(PICO_SDK_PATH "${PICO_SDK_PATH}")
get_filename_component(PICO_SDK_PATH "${PICO_SDK_PATH}" REALPATH BASE_DIR "${CMAKE_BINARY_DIR}")
if (NOT EXISTS ${PICO_SDK_PATH})
    message(FATAL_ERROR "Directory '${PICO_SDK_PATH}' not found")
endif ()

set(PICO_SDK_INIT_CMAKE_FILE ${PICO_SDK_PATH}/pico_sdk_init.cmake)
if (NOT EXISTS ${PICO_SDK_INIT_CMAKE_FILE})
    message(FATAL_ERROR "Directory '${PICO_SDK_PATH}' does not appear to contain the Raspberry Pi Pico SDK")
endif ()

set(PICO_SDK_PATH ${PICO_SDK_PATH} CACHE PATH "Path to the Raspberry Pi Pico SDK" FORCE)
include(${PICO_SDK_INIT_CMAKE_FILE})

cmake_minimum_required(VERSION 3.22)
project(pcbu_common)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(Threads REQUIRED)
find_package(OpenSSL 3 REQUIRED)

add_compile_definitions("PCBU_DEBUG=$<CONFIG:Debug>")
add_compile_definitions(TARGET_ARCH="${TARGET_ARCH}")
if(WIN32)
    add_compile_definitions(WINDOWS _WIN32_WINNT=0x0A00)
    set(PLATFORM_LIBS
            crypt32
            Ws2_32
            Advapi32
            Iphlpapi
            Sensapi
            Netapi32
            Wtsapi32
            Bthprops
            ole32
            Shell32
    )
    set(PLATFORM_SRC
            src/connection/unlock/clients/BTUnlockClient.cpp
            src/connection/unlock/servers/BTUnlockServer.cpp
            src/platform/PlatformHelper.Win.cpp
            src/platform/BluetoothHelper.Win.cpp
            src/utils/RegistryUtils.cpp
            src/utils/RegistryUtils.h
    )
elseif(APPLE)
    add_compile_definitions(APPLE)
    enable_language(OBJCXX)
    set(PLATFORM_LIBS
            "-framework Foundation"
            "-framework CoreServices"
            "-framework SystemConfiguration"
            "-framework Carbon"
            "-framework IOKit"
            "-framework IOBluetooth"
            "-framework Security"
    )
    set(PLATFORM_SRC
            src/connection/unlock/clients/BTUnlockClient.Mac.mm
            src/connection/unlock/servers/BTUnlockServer.Mac.mm
            src/platform/PlatformHelper.Mac.cpp
            src/platform/BluetoothHelper.Mac.mm
    )
elseif(UNIX)
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(libxcrypt IMPORTED_TARGET REQUIRED libxcrypt)

    add_compile_definitions(LINUX HAVE_CRYPT_H)
    set(PLATFORM_LIBS
            PkgConfig::libxcrypt
            bluetooth
    )
    set(PLATFORM_SRC
            src/connection/unlock/clients/BTUnlockClient.cpp
            src/connection/unlock/servers/BTUnlockServer.cpp
            src/platform/PlatformHelper.Linux.cpp
            src/platform/BluetoothHelper.Linux.cpp
    )
endif()

add_library(pcbu_common
        src/connection/SocketDefs.h
        src/connection/BaseConnection.cpp
        src/connection/BaseConnection.h
        src/connection/UDPBroadcaster.cpp
        src/connection/UDPBroadcaster.h
        src/connection/pairing/PairingServer.cpp
        src/connection/pairing/PairingServer.h
        src/connection/pairing/UDPPairingBroadcaster.cpp
        src/connection/pairing/UDPPairingBroadcaster.h
        src/connection/pairing/PairingStructs.h
        src/connection/unlock/BaseUnlockConnection.h
        src/connection/unlock/BaseUnlockConnection.cpp
        src/connection/unlock/UDPUnlockBroadcaster.cpp
        src/connection/unlock/UDPUnlockBroadcaster.h
        src/connection/unlock/clients/TCPUnlockClient.h
        src/connection/unlock/clients/TCPUnlockClient.cpp
        src/connection/unlock/clients/BTUnlockClient.h
        src/connection/unlock/servers/BTUnlockServer.h
        src/connection/unlock/servers/TCPUnlockServer.cpp
        src/connection/unlock/servers/TCPUnlockServer.h
        src/handler/KeyScanner.h
        src/handler/KeyScanner.cpp
        src/handler/UnlockHandler.h
        src/handler/UnlockHandler.cpp
        src/platform/PlatformHelper.h
        src/platform/NetworkHelper.cpp
        src/platform/NetworkHelper.h
        src/platform/BluetoothHelper.h
        src/shell/Shell.cpp
        src/shell/Shell.h
        src/storage/AppSettings.cpp
        src/storage/AppSettings.h
        src/storage/LoggingSystem.cpp
        src/storage/LoggingSystem.h
        src/storage/PairedDevicesStorage.cpp
        src/storage/PairedDevicesStorage.h
        src/storage/PairingMethod.h
        src/utils/CryptUtils.cpp
        src/utils/CryptUtils.h
        src/utils/StringUtils.cpp
        src/utils/StringUtils.h
        src/utils/Utils.cpp
        src/utils/Utils.h
        src/utils/AppInfo.cpp
        src/utils/AppInfo.h
        src/utils/RestClient.cpp
        src/utils/RestClient.h
        src/utils/LocaleHelper.cpp
        src/utils/LocaleHelper.h
        src/utils/I18n.cpp
        src/utils/I18n.h
        ${PLATFORM_SRC}
)
target_include_directories(pcbu_common PUBLIC
        src
        ${cpp_httplib_SOURCE_DIR}
)
target_link_libraries(pcbu_common PUBLIC
        Threads::Threads
        nlohmann_json::nlohmann_json
        spdlog::spdlog
        OpenSSL::Crypto
        OpenSSL::SSL
        Boost::system
        Boost::filesystem
        Boost::process
        Boost::asio
        ${PLATFORM_LIBS}
)

# Commit header
set(COMMIT_HEADER_FILE "${CMAKE_CURRENT_SOURCE_DIR}/src/generated/commit.h")
cmake_path(GET CMAKE_CURRENT_SOURCE_DIR PARENT_PATH PCBU_ROOT)
add_custom_command(
    OUTPUT ${COMMIT_HEADER_FILE}
    COMMAND sh -c "echo '#ifndef PCBU_COMMIT_H' > ${COMMIT_HEADER_FILE} && \
                echo '#define PCBU_COMMIT_H' >> ${COMMIT_HEADER_FILE} && \
                echo '#include <string_view>' >> ${COMMIT_HEADER_FILE} && \
                echo 'constexpr std::string_view GIT_COMMIT_HASH = \"'$(git rev-parse --short HEAD)'\";' >> ${COMMIT_HEADER_FILE} && \
                echo 'constexpr std::string_view GIT_BRANCH = \"'$(git rev-parse --abbrev-ref HEAD)'\";' >> ${COMMIT_HEADER_FILE} && \
                echo '#endif' >> ${COMMIT_HEADER_FILE}"
    WORKING_DIRECTORY ${PCBU_ROOT}
    DEPENDS ${PCBU_ROOT}/.git/index
    COMMENT "Updating commit hash"
    VERBATIM
)
add_custom_target(update_commit DEPENDS ${COMMIT_HEADER_FILE})
add_dependencies(pcbu_common update_commit)

# Italock: textos e traducoes embutidos (res/pt_BR.json com as chaves antigas + res/i18n/<codigo>.json). Viram arrays de
# bytes (cabe qualquer tamanho; literal de texto grande estoura o limite do MSVC) e sao lidos so quando usados. O script
# confere o JSON: arquivo invalido para a compilacao (nunca chega quebrado na tela de bloqueio).
function(embed_json TARGET JSON_FILE VARIABLE_PREFIX)
    get_filename_component(JSON_PATH ${JSON_FILE} ABSOLUTE)
    set(GENERATED_HEADER ${CMAKE_CURRENT_SOURCE_DIR}/src/generated/${VARIABLE_PREFIX}.h)
    set(EMBED_SCRIPT ${CMAKE_CURRENT_SOURCE_DIR}/../cmake/EmbedJson.cmake)
    file(MAKE_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/src/generated)
    add_custom_command(
        OUTPUT ${GENERATED_HEADER}
        COMMAND ${CMAKE_COMMAND} -DIN=${JSON_PATH} -DOUT=${GENERATED_HEADER} -DNAME=${VARIABLE_PREFIX} -P ${EMBED_SCRIPT}
        DEPENDS ${JSON_PATH} ${EMBED_SCRIPT}
        COMMENT "Generating JSON header: ${GENERATED_HEADER}"
        VERBATIM
    )
    add_custom_target(generate_json_header_${VARIABLE_PREFIX} DEPENDS ${GENERATED_HEADER})
    add_dependencies(${TARGET} generate_json_header_${VARIABLE_PREFIX})
endfunction()
embed_json(pcbu_common res/pt_BR.json I18N_PT_BR)
foreach(ITALOCK_LANG en es fr de it ja ko zh_CN ru pl hi ar)
    string(TOUPPER ${ITALOCK_LANG} ITALOCK_LANG_UPPER)
    embed_json(pcbu_common res/i18n/${ITALOCK_LANG}.json I18N_${ITALOCK_LANG_UPPER})
endforeach()

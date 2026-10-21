cmake_minimum_required(VERSION 3.22)

# Italock: "destravar SSH pelo celular" (experimental, Linux). Porte do pcbu-ssh-askpass do PulseUnlock v4
# (MeisApps, GPL versao 3), adaptado: senhas guardadas a parte em /etc/italock/ssh_servers.json e comandos em portugues.
project(italock_ssh)
set(CMAKE_CXX_STANDARD 23)

if(WIN32)
    add_compile_definitions(WINDOWS)
elseif(APPLE)
    add_compile_definitions(APPLE)
elseif(UNIX)
    add_compile_definitions(LINUX)
endif()

add_executable(italock-ssh
        src/main.cpp
)
target_link_libraries(italock-ssh PRIVATE pcbu_common)

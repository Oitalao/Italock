# Italock: transforma um JSON em cabecalho C++ com um array de bytes.
# Uso: cmake -DIN=<arquivo.json> -DOUT=<cabecalho.h> -DNAME=<identificador> -P EmbedJson.cmake
# Confere antes: JSON invalido, que nao seja objeto ou com valor que nao seja texto para a compilacao.
file(READ "${IN}" content)
string(JSON kind ERROR_VARIABLE err TYPE "${content}")
if(err OR NOT kind STREQUAL "OBJECT")
    message(FATAL_ERROR "JSON invalido em ${IN}: ${err}")
endif()
string(JSON count LENGTH "${content}")
if(count GREATER 0)
    math(EXPR last "${count} - 1")
    foreach(i RANGE ${last})
        string(JSON key MEMBER "${content}" ${i})
        string(JSON valueType TYPE "${content}" "${key}")
        if(NOT valueType STREQUAL "STRING")
            message(FATAL_ERROR "Em ${IN}, o valor de \"${key}\" nao e texto.")
        endif()
    endforeach()
endif()
file(READ "${IN}" hex HEX)
string(LENGTH "${hex}" hexLength)
math(EXPR size "${hexLength} / 2")
string(REGEX REPLACE "([0-9a-f][0-9a-f])" "0x\\1," bytes "${hex}")
string(REGEX REPLACE "((0x[0-9a-f][0-9a-f],){32})" "\\1\n" bytes "${bytes}")
file(WRITE "${OUT}.tmp"
    "// Gerado por cmake/EmbedJson.cmake a partir de ${IN}. Nao editar.\n"
    "#pragma once\n#include <cstddef>\n"
    "static const unsigned char ${NAME}_BYTES[] = {\n${bytes}0x00};\n"
    "static constexpr std::size_t ${NAME}_SIZE = ${size};\n")
file(RENAME "${OUT}.tmp" "${OUT}")

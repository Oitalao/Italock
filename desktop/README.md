# Ítalock para computador

Programa do Ítalock para Windows e Linux. É um fork do [PC Bio Unlock](https://github.com/MeisApps/pcbu-desktop) (MeisApps, GPL 3.0).

| Pasta | O que tem |
| --- | --- |
| `desktop/` | Janela do programa e ícone da bandeja (Qt Quick) |
| `common/` | Pareamento, conexões e armazenamento, usados por todas as partes |
| `natives/win-pcbiounlock/` | Módulo da tela de bloqueio do Windows (credential provider) |
| `natives/pam-pcbiounlock/`, `natives/pcbu-auth/` | Módulo PAM do Linux |
| `natives/italock-agent/` | Agente que recebe os pedidos de travar e destravar do celular |
| `natives/italock-ssh/` | Senhas de SSH confirmadas pelo celular (experimental) |
| `pkg/` | Instaladores (Inno Setup no Windows, `.run` no Linux) |

Como compilar: [`../docs/COMPILAR.md`](../docs/COMPILAR.md).

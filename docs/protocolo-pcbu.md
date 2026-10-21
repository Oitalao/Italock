# Protocolo PC Bio Unlock (pcbu-desktop) — especificação para cliente Android

Fonte: [pcbu-desktop](https://github.com/MeisApps/pcbu-desktop), commit `11c9532` (app 3.3.4, `common/src/utils/AppInfo.cpp:9`).
Todas as citações `arquivo:linha` são relativas à raiz do repositório. Nada foi adivinhado; o que o código não
define está em "Questões em aberto" (§7).

Convenções: "PC" = app desktop / módulo de login; "celular" = cliente Android a implementar.
JSON é gerado com `nlohmann::json::dump()` compacto; como `nlohmann::json` usa `std::map`, **as chaves saem em
ordem alfabética** (ex.: `{"encKey":…,"method":…,"port":…,"serverId":…}`). O parser do PC não depende de ordem.

---

## 1. Portas, padrões e quem escuta onde

### 1.1 Configurações do PC (`app_settings.json`)

Arquivo: `%ProgramData%\PCBioUnlock\app_settings.json` (Windows) ou `/etc/pc-bio-unlock/app_settings.json`
(`common/src/storage/AppSettings.cpp:18-27`, `AppSettings.h:45`). Padrões (`AppSettings.cpp:76-88`):

| Campo | Padrão | Uso |
|---|---|---|
| `pairingDiscoveryPort` | 43297 (também fallback em `:55`) | Porta **UDP de destino** do beacon de pareamento; vai no QR novo como `port` |
| `pairingServerPort` | 43295 | Porta **TCP** em que o PC escuta durante o pareamento |
| `unlockServerPort` | 43296 | Porta **TCP** em que o PC escuta no desbloqueio UDP/MANUAL_UDP/CLOUD_TCP |
| `clientConnectTimeout` | 5 s | Timeout de `connect()` do PC → celular (TCP/BT) |
| `clientConnectRetries` | 2 | Tentativas extras de conexão (total 3) |
| `clientSocketTimeout` | 120 s | SO_RCVTIMEO/SO_SNDTIMEO (ver §7, provavelmente sem efeito) |
| `serverIP` / `serverMAC` | `"auto"` / — | Interface usada no QR **legado** (`NetworkHelper.cpp:238-261`) |

Portas do **celular** (`tcpPort`, `udpPort`, `udpManualPort`) **não têm padrão no desktop**: o celular as escolhe e
envia no pacote de pareamento (`Packets.h:26-28`, gravadas em `PairingServer.cpp:172-177`).

Alterar `unlockServerPort` na UI apaga todos os dispositivos UDP/MANUAL_UDP (`desktop/qml/forms/SettingsForm.qml:463-465`,
`desktop/src/ui/SettingsForm.cpp:75-79`, texto `common/res/en_US.json:66`).

### 1.2 Enum `PairingMethod` (`common/src/storage/PairingMethod.h:7-43`)

Serializado como string: `"TCP"`, `"BLUETOOTH"`, `"CLOUD_TCP"`, `"CLOUD_BT"`, `"UDP"`, `"MANUAL_UDP"`. String
desconhecida vira `TCP` (`:41-42`). A UI só oferece `UDP`, `TCP`, `BLUETOOTH` (tipo AUTO) e `MANUAL_UDP` (tipo MANUAL)
(`desktop/qml/pairing/PairingMethodForm.qml:29,44,60,76`); padrão `UDP` (`desktop/src/ui/PairingForm.cpp:208-210,134-137`).

### 1.3 Quem escuta onde

| Fase / método | PC | Celular |
|---|---|---|
| Pareamento (todos os métodos, inclusive BLUETOOTH) | Escuta TCP `0.0.0.0:pairingServerPort` (`PairingServer.cpp:76-78`), até 10 conexões simultâneas (`:19,97`). Se não-legado, envia beacon UDP a cada 2 s para `broadcast:pairingDiscoveryPort` (`UDPPairingBroadcaster.cpp:9,14-15,34`; `PairingForm.cpp:117-120`) | Escuta UDP na porta `port` do QR (não-legado); conecta TCP ao PC |
| Desbloqueio `TCP` | **Cliente**: conecta em `device.ipAddress:device.tcpPort` (`UnlockHandler.cpp:35-37`) | **Servidor** TCP em `tcpPort` |
| Desbloqueio `BLUETOOTH` | **Cliente** RFCOMM para `device.bluetoothAddress`, serviço UUID `62182bf7-97c8-45f9-aa2c-53c5f2008bdf` (`BTUnlockClient.cpp:55,65-67`) | **Servidor** RFCOMM com esse UUID no SDP |
| Desbloqueio `UDP` | Broadcast UDP (texto puro) para porta `device.udpPort` + servidor TCP `0.0.0.0:unlockServerPort` (`UnlockHandler.cpp:42-50,61-65`; `TCPUnlockServer.cpp:69-71`) | Escuta UDP em `udpPort`; conecta TCP em `pcbuIP:pcbuPort` |
| Desbloqueio `MANUAL_UDP` | Igual a UDP, mas destino `device.udpManualPort` e `isManual=true` (`UnlockHandler.cpp:45-46`) | Escuta UDP em `udpManualPort` |
| `CLOUD_TCP` | Só o servidor TCP `unlockServerPort`, sem broadcast (`UnlockHandler.cpp:48-50`) | — (nenhuma nuvem implementada no desktop) |
| `CLOUD_BT` | Não suportado: cai no `default` "Invalid pairing method" (`UnlockHandler.cpp:51-54`) | — |

Obs.: o `switch` tem *fall-through* intencional de `UDP`/`MANUAL_UDP` para `CLOUD_TCP` (`UnlockHandler.cpp:42-50`).
`BTUnlockServer` (PC como servidor RFCOMM; canal 27 no Linux, `BTUnlockServer.cpp:54`) existe mas **nunca é instanciado**.
O único tráfego para fora da LAN é checagem de update (`common/src/utils/RestClient.cpp:10`).

---

## 2. Pareamento

### 2.1 Dados gerados pelo PC na tela do QR (`PairingForm.cpp:102-115`)

- `serverId = RandomString(8)`; `encKey = RandomString(64)` (`:105-106`).
- `RandomString`: cada byte de `RAND_bytes` mod 88 indexa o alfabeto
  `a-z A-Z 0-9 !@#$%^&*()-_=+[]{}|;:,.<>?` (`common/src/utils/StringUtils.cpp:74-87`). ASCII puro, sem `"` nem `\`.
- O servidor TCP é (re)iniciado e, se não-legado, o beacon UDP (`:117-120`).

### 2.2 QR code (`PairingStructs.h:9-43`, `PairingForm.cpp:42-49`)

Conteúdo do QR = texto JSON (ECC MEDIUM, `desktop/src/utils/QRUtils.cpp:8`).

**Novo (padrão)** — `PairingQRData`:
```json
{"encKey":"<64 chars>","method":"UDP","port":43297,"serverId":"<8 chars>"}
```
| Campo | Tipo | Valor |
|---|---|---|
| `serverId` | string | id aleatório de 8 chars |
| `port` | número (uint16) | `pairingDiscoveryPort` (UDP em que o **celular** deve escutar) |
| `method` | string | `PairingMethod` escolhido |
| `encKey` | string | senha de 64 chars (chave de pareamento **e** chave permanente do dispositivo) |

**Legado** (`useLegacyPairing`, checkbox em `PairingMethodTypeForm.qml:65`) — `LegacyPairingQRData`:
```json
{"encKey":"<64>","ip":"192.168.0.10","method":"UDP","port":43295}
```
`ip` = IP da interface salva (`NetworkHelper::GetSavedNetworkInterface`), `port` = `pairingServerPort` (TCP). Sem beacon.
O celular distingue os formatos pela presença de `serverId` vs `ip`.

### 2.3 Código de pareamento manual (`PairingForm.cpp:37-40`)

`código = WithSeperators(Base32(payloadJSON), "-", 5)` — o **mesmo JSON** do QR (novo ou legado).
- Base32: alfabeto RFC 4648 `A-Z2-7`, maiúsculo, **sem `=`**; bits restantes completados com zeros à direita
  (`StringUtils.cpp:113-129`).
- Agrupado em blocos de 5 separados por `-` (`StringUtils.cpp:89-94`).
- Decodificação no celular: remover `-` (e espaços), maiúsculas, Base32 sem padding, descartar bits finais < 8, parsear JSON.

### 2.4 Descoberta (somente QR novo) — beacon UDP

`UDPPairingBroadcaster::SendToTarget` (`UDPPairingBroadcaster.cpp:22-35`):
- Plaintext: `{"ip":"<IP da interface de origem>","port":<pairingServerPort>,"serverId":"<serverId>"}` (`Packets.h:149-157`).
- Datagrama = **bytes binários crus** de `EncryptAESPacket(plaintext, encKey)` (§2.6). Sem cabeçalho/framing, sem hex.
- Destino: para cada interface IPv4 privada (10/8, 172.16-31, 192.168/16 via regex `NetworkHelper.cpp:36-37`) ou
  169.254/16, endereço de broadcast direcionado `ip | ~mask` (fallback `255.255.255.255`), porta `pairingDiscoveryPort`;
  socket ligado ao IP de origem (`NetworkHelper.cpp:276-300`, `UDPBroadcaster.cpp:49-84`). Um datagrama por interface
  a cada 2000 ms (`UDPBroadcaster.cpp:27-47`). Lista de interfaces calculada uma vez no início (`UDPBroadcaster.cpp:29`).

Celular: escutar UDP em `QR.port`; para cada datagrama tentar decifrar com `encKey` (falha → ignorar); checar
`serverId == QR.serverId`; conectar TCP em `ip:port`. Pode chegar um beacon por interface (IPs diferentes).

### 2.5 Framing TCP/RFCOMM (idêntico em pareamento e desbloqueio)

`BaseConnection::WritePacket/ReadPacket` (`common/src/connection/BaseConnection.cpp:48-138`):

| Offset | Tamanho | Conteúdo |
|---|---|---|
| 0 | 8 | Magic `DB 06 5A C7 AF DF A4 CC` (uint64 big-endian de `0xDB065AC7AFDFA4CC`, `Packets.h:12`) |
| 8 | 2 | Packet ID, uint16 **big-endian** |
| 10 | 2 | Tamanho do payload N, uint16 **big-endian** (máx. 65535; N=0 é rejeitado, `:89-92`) |
| 12 | N | Payload |

IDs (`Packets.h:14-19`): `0x50` PAIR_INIT (celular→PC), `0x51` PAIR_RESPONSE (PC→celular), `0xB0` DEVICE_ID,
`0xB1` UNLOCK_REQUEST, `0xB2` UNLOCK_RESPONSE.
O leitor do PC ressincroniza procurando o magic byte a byte e descarta lixo antes dele (`:52-67`). O PC usa TCP_NODELAY.

### 2.6 Criptografia (`common/src/utils/CryptUtils.cpp`)

**`EncryptAES` bruto** (`:151-223`) — formato do blob:
```
IV (16 bytes aleatórios) || salt (16 bytes aleatórios) || ciphertext (= len(plaintext)) || tag GCM (16 bytes)
```
- Algoritmo: AES-256-GCM, IV de **16 bytes** (`EVP_CTRL_GCM_SET_IVLEN 16`, `:184`), sem AAD, tag 16 bytes no fim (`:216`).
- Chave: `PBKDF2-HMAC-SHA256(password = bytes ASCII da string (até o 1º NUL), salt, 65535 iterações, 32 bytes)`
  (`:19-23,293-302`). Nova derivação a cada mensagem (salt aleatório por mensagem).
- Kotlin: `Cipher.getInstance("AES/GCM/NoPadding")` + `GCMParameterSpec(128, iv16)`; `doFinal` já produz `ct||tag`.

**`EncryptAESPacket`** (`:48-75`) — usado em todas as mensagens de protocolo:
`plaintext_real = int64_BE(unixTimeMs) || dados` e depois `EncryptAES` bruto acima.
**`DecryptAESPacket`** (`:77-106`): decifra; se resultado ≤ 8 bytes → erro; lê timestamp BE; se
`|agora − ts| > 120000 ms` (`CryptUtils.h:9`) → `INVALID_TIMESTAMP`; retorna bytes após os 8 primeiros.
Limites: entrada cifrada ≤ 2048 bytes (`:78`); dados a cifrar ≤ 1992 bytes (buffer 2048 − 8 − 48, `:49,154`).

**`EncryptAES(string)` / `DecryptAES(string)`** (`:108-149`): mesmo blob **sem timestamp**, codificado em hex. Usado só para
guardar a senha do Windows (§2.8).

**Hex** (`StringUtils.cpp:96-111`): PC emite **maiúsculo**; aceita maiúsculo/minúsculo; tamanho ímpar/char inválido → vazio.

**"Sha256"** é na verdade **SHA3-256** (`CryptUtils.cpp:30,38`), saída hex maiúscula (64 chars).

### 2.7 Troca TCP de pareamento (`common/src/connection/pairing/PairingServer.cpp:133-238`)

Uma conexão = um pedido + uma resposta; depois o PC fecha (`:209-211`).

1. Celular conecta em `ip:port` (beacon ou QR legado).
2. **Celular → PC**: frame ID `0x50`, payload = bytes crus de `EncryptAESPacket(JSON, encKey)`.
   O PC **não verifica o ID** do pacote (`:214-224`), mas use `0x50`.
   JSON `PacketPairInit` (`Packets.h:21-51`):
   ```json
   {"cloudToken":"","deviceName":"Pixel 8","deviceUUID":"<id estável do celular>","ipAddress":"192.168.0.50",
    "protoVersion":"4.0.0","tcpPort":<uint16>,"udpManualPort":<uint16>,"udpPort":<uint16>}
   ```
   - `protoVersion` obrigatório; ausente → erro "packet parse" (`:149-151`).
   - Os demais são lidos em sequência dentro de um único `try` (`Packets.h:36-45`): na ordem `deviceUUID, deviceName,
     ipAddress, tcpPort, udpPort, udpManualPort, cloudToken`; se um faltar ou tiver tipo errado, **ele e todos os seguintes
     ficam vazios/0 silenciosamente**. Envie todos, strings como string (inclusive `cloudToken:""`), portas como número.
   - `deviceUUID` vazio → PC gera um aleatório (`:154-157`), o que quebra a re-paridade idempotente.
   - Não há campo de endereço Bluetooth: para `BLUETOOTH` o PC usa o endereço escolhido na própria UI (`:176`,
     `PairingForm.cpp:113`). O fluxo BT também passa pelo QR/TCP (`PairingForm.cpp:68-81`).
   - Se a decifragem falhar (chave/relógio), o PC fecha **sem responder** (`:140-147`).
3. PC valida `CompareVersion("4.0.0", protoVersion) == 0` (`:152-153`, §5).
4. PC gera `passwordKey = RandomString(64)` e `passwordEnc = EncryptAES(senhaWindows, passwordKey)` (`:159-162`).
   `device.id = hex(SHA3-256(machineID + deviceUUID + userName))` (`:165`).
5. **PC → celular**: frame ID `0x51`, payload = bytes crus de `EncryptAESPacket(JSON, encKey)` (`:191,226-238`).
   JSON `PacketPairResponse` (`Packets.h:53-82`, preenchido em `PairingServer.cpp:179-189`):
   ```json
   {"data":{"deviceId":"<64 hex maiúsc.>","deviceName":"<hostname do PC>","deviceOS":"Windows",
            "macAddresses":["AA:BB:CC:DD:EE:FF"],"pairingMethod":"UDP","passwordKey":"<64 chars>",
            "unlockServerPort":43296,"userName":"DOMINIO\\usuario"},
    "errMsg":""}
   ```
   - `deviceOS` ∈ `Windows|Linux|macOS` (`AppInfo.cpp:23-33`).
   - `macAddresses`: interfaces **cabeadas** com IP privado e MAC ≠ zero, formato `%02X:` maiúsculo — para Wake-on-LAN
     (`NetworkHelper.cpp:54,132,263-274`). No Windows a detecção de cabo é `IF_TYPE_ETHERNET_CSMACD` (`:62`).
   - `userName`: no Windows a UI exige `DOMINIO\usuario` ou UPN com `@` (`PairingForm.cpp:230-236`).
   - Erro: `errMsg` = mensagem localizada, `data` com valores default (`pairingMethod:"TCP"`, strings vazias, porta 0,
     `macAddresses:[]`) (`:201-208`). Sucesso ⇔ `errMsg == ""`.
6. Só após escrever a resposta com sucesso o PC grava o dispositivo (`:191-192`) e, no Windows, define o credential provider
   padrão `{74A23DE2-B81D-46EC-E129-CD32507ED716}` (`:194-199`).

O celular deve guardar: `deviceId`, `encKey`, `passwordKey`, `deviceName`, `deviceOS`, `userName`, `unlockServerPort`,
`macAddresses`, `pairingMethod`, IP/porta do PC (do beacon ou QR).

### 2.8 Onde fica a senha do Windows

- PC guarda `passwordEnc` (hex, sem timestamp) e `encryptionKey` (= `encKey`, **em claro**) em `paired_devices.json`;
  **não** guarda `passwordKey` (`PairingServer.cpp:164-177`, `PairedDevicesStorage.cpp:102-133`). Proteção: no Windows ACE
  DENY para `BUILTIN\Users` (S-1-5-32-545) exceto durante leitura/escrita; Linux/mac `chown root` + `chmod 600`
  (`PairedDevicesStorage.cpp:135-163`).
- O celular **nunca recebe a senha**; recebe só `passwordKey` e o devolve a cada desbloqueio. O PC decifra
  `passwordEnc` com ele (`UnlockHandler.cpp:180-186`) e valida a senha normalmente (ex. `natives/pcbu-auth/src/main.cpp:66`).

---

## 3. Desbloqueio

### 3.1 Mensagens (comuns a TCP, UDP e BT)

**DEVICE_ID `0xB0`** (celular→PC, só no modo servidor do PC): payload = bytes ASCII de `deviceId`, sem JSON, sem cifra,
sem terminador (`BaseUnlockConnection.cpp:91-93`). Id desconhecido → `DATA_ERROR` e a tentativa termina (`:94-98`).

**UNLOCK_REQUEST `0xB1`** (PC→celular), JSON em claro (`Packets.h:84-92`, `BaseUnlockConnection.cpp:116-151`):
```json
{"deviceId":"<64 hex>","encData":"<HEX maiúsc.>","protoVersion":"3.0.0"}
```
`encData = hex(EncryptAESPacket(inner, encryptionKey))`, com `inner` (`Packets.h:94-102`):
```json
{"program":"Windows-Login","unlockToken":"<64 chars>","user":"DOMINIO\\usuario"}
```
- `unlockToken = RandomString(64)`, um por instância de conexão (`BaseUnlockConnection.cpp:7`).
- `user` = usuário solicitado; `program`: `"Windows-Login"` no credential provider
  (`natives/win-pcbiounlock/src/CUnlockListener.cpp:126`), linha de comando do processo pai (≤256 chars) ou `"PID n"` no
  Linux/mac (`natives/pcbu-auth/src/main.cpp:17-54,64`), texto localizado "Unlock test" no teste (`desktop/src/ui/UnlockTestWindow.cpp:30`).

**UNLOCK_RESPONSE `0xB2`** (celular→PC), JSON em claro (`Packets.h:104-119`) — **ambas as chaves obrigatórias** (se faltar
uma, o parse falha → `DATA_ERROR`):
```json
{"encData":"<HEX>","error":""}
```
`encData = hex(EncryptAESPacket(inner, encryptionKey))`, com `inner` (`Packets.h:121-136`, ambas obrigatórias):
```json
{"passwordKey":"<passwordKey do pareamento>","unlockToken":"<token recebido>"}
```
Erro: `{"encData":"","error":"CANCEL"}` etc. Mapeamento (`BaseUnlockConnection.cpp:164-182`):

| `error` | `UnlockState` |
|---|---|
| `""` | processa `encData` |
| `CANCEL` | CANCELED |
| `NOT_PAIRED` | NOT_PAIRED_ERROR |
| `APP_ERROR` | APP_ERROR |
| `TIME_ERROR` | TIME_ERROR |
| `DATA_ERROR` | DATA_ERROR |
| `PROTOCOL_ERROR` | PROTOCOL_ERROR |
| outro | UNK_ERROR |

Validação no PC (`:185-219`): hex→decifra (timestamp fora de ±120 s → TIME_ERROR; outra falha → DATA_ERROR) → parse →
`unlockToken` idêntico → SUCCESS, senão UNK_ERROR. Em seguida, `passwordKey` errado → DATA_ERROR (`UnlockHandler.cpp:180-186`).
`0xB2` recebido antes de `0xB1` é ignorado (`BaseUnlockConnection.cpp:85-88`).

### 3.2 `UnlockState` (`common/src/handler/UnlockState.h:7-21`)

| Valor | Nome | Origem |
|---|---|---|
| 0 | UNKNOWN | em andamento |
| 1 | SUCCESS | token confere |
| 2 | CANCELED | `CANCEL` do celular, Ctrl+Alt esquerdos no PC (`UnlockHandler.cpp:159-162`), outra conexão já teve sucesso ou abortada (`:139-143`) |
| 3 | TIMEOUT | sem cliente conectado após 120 s (`UnlockHandler.cpp:155-158`) ou erro de timeout de socket |
| 4 | CONNECT_ERROR | conexão fechada/recusada, falha de connect/SDP |
| 5 | TIME_ERROR | timestamp inválido ou `TIME_ERROR` do celular |
| 6 | DATA_ERROR | parse/decifra/deviceId inválido, ou `DATA_ERROR` |
| 7 | NOT_PAIRED_ERROR | nenhum dispositivo para o usuário (`UnlockHandler.cpp:66-71`) ou `NOT_PAIRED` |
| 8 | APP_ERROR | `APP_ERROR` do celular |
| 9 | START_ERROR | falha ao iniciar conexão |
| 10 | PORT_ERROR | `bind()` do servidor TCP falhou (`TCPUnlockServer.cpp:74`) |
| 11 | PROTOCOL_ERROR | `PROTOCOL_ERROR` do celular |
| 12 | UNK_ERROR | demais |

Todos os dispositivos do usuário são tentados em paralelo; o primeiro SUCCESS cancela os demais (`UnlockHandler.cpp:81-94,139-143`).
Busca de usuário no Windows é case-insensitive e aceita UPN (`PairedDevicesStorage.cpp:28-48`).

### 3.3 Método UDP / MANUAL_UDP (PC servidor)

1. PC envia, a cada 2 s, por interface e por dispositivo, datagrama **JSON em claro** (sem cifra, sem framing)
   (`UDPUnlockBroadcaster.cpp:6,20-30`, `Packets.h:138-147`):
   ```json
   {"deviceId":"<64 hex>","isManual":false,"pcbuIP":"192.168.0.10","pcbuPort":43296}
   ```
   Destino: broadcast direcionado da interface (§2.4) : `udpPort` (UDP) ou `udpManualPort` (MANUAL_UDP, `isManual:true`).
   O broadcast para enquanto houver cliente conectado e recomeça se ele sair (`UnlockHandler.cpp:144-150,164-170`).
2. Celular, se `deviceId` for de um pareamento seu, conecta TCP em `pcbuIP:pcbuPort` (deduplicar: chegam vários datagramas).
3. Celular → `0xB0` com `deviceId`.
4. PC → `0xB1` (§3.1).
5. Celular valida, pede biometria, → `0xB2`.
6. PC encerra fechando o socket (no modo servidor não envia `"CLOSE"`, `TCPUnlockServer.cpp:34-40`).

Servidor aceita até 10 clientes simultâneos (`TCPUnlockServer.cpp:14,90-93`). Obs.: `GetDeviceByID` não filtra pelo usuário
solicitado (`BaseUnlockConnection.cpp:93`); `pcbu-auth` confere o usuário depois (`main.cpp:66`).

### 3.4 Método TCP (PC cliente)

1. PC conecta em `ipAddress:tcpPort` gravados no pareamento (IP não é atualizado depois; `en_US.json:148`), timeout 5 s,
   até 3 tentativas (`TCPUnlockClient.cpp:44-126`).
2. Assim que conecta, PC → `0xB1` imediatamente (sem `0xB0`) (`BaseUnlockConnection.cpp:43-53`).
3. Celular → `0xB2`.
4. Ao terminar (qualquer resultado), PC escreve **5 bytes crus `CLOSE`** (sem framing) e fecha (`TCPUnlockClient.cpp:33-42`).
   O celular deve tolerar/ignorar bytes fora de frame e tratar `CLOSE`/EOF como fim (fechar prompt).

### 3.5 Método BLUETOOTH (PC cliente RFCOMM)

- Windows: `SOCKADDR_BTH` com `serviceClassId = 62182bf7-97c8-45f9-aa2c-53c5f2008bdf` (resolução SDP pelo SO)
  (`BTUnlockClient.cpp:55-62`). Linux: busca SDP pelo mesmo UUID e usa o canal RFCOMM encontrado (1–30)
  (`BTUnlockClient.cpp:65-77`, `BluetoothHelper.Linux.cpp:60-146`).
- Endereço BT: string `AA:BB:CC:DD:EE:FF` escolhida na UI do PC (`BluetoothHelper.Win.cpp:96-103`).
- Sequência, framing, cifra e `CLOSE` idênticos ao TCP (§3.4) (`BTUnlockClient.cpp:39-40,141`).
- Android: `listenUsingRfcommWithServiceRecord(nome, UUID("62182bf7-97c8-45f9-aa2c-53c5f2008bdf"))`.
- O cliente BT de macOS parece incompleto (escrita comentada em `BTUnlockClient.Mac.mm:68`).

### 3.6 Timeouts

| O quê | Valor | Fonte |
|---|---|---|
| Janela de timestamp (ambos os sentidos) | ±120 000 ms | `CryptUtils.h:9`, `CryptUtils.cpp:96-100` |
| PC esperando cliente conectar | 120 s → TIMEOUT | `UnlockHandler.cpp:155-158` |
| Connect PC→celular | 5 s × (1+2) | `AppSettings.cpp:82-83` |
| Após o celular conectar | **nenhum timeout efetivo no PC** (só cancelamento) — ver §7 | `TCPUnlockClient.cpp:79`, `BaseConnection.cpp:140-156` |
| Intervalo dos broadcasts | 2000 ms | `UDPUnlockBroadcaster.cpp:6`, `UDPPairingBroadcaster.cpp:9` |

---

## 4. Armazenamento de dispositivos e limites

`paired_devices.json` (mesma pasta de §1.1; `PairedDevicesStorage.h:44`), array de objetos (`PairedDevicesStorage.cpp:102-121`):

| Campo | Tipo | Origem |
|---|---|---|
| `id` | string (64 hex) | SHA3-256(machineID+deviceUUID+userName) |
| `pairingMethod` | string | UI do PC |
| `deviceName` | string | celular (`deviceName`) |
| `userName` | string | UI do PC |
| `passwordEnc` | string hex | senha cifrada com `passwordKey` |
| `encryptionKey` | string | `encKey` do QR |
| `ipAddress` | string | celular |
| `tcpPort`, `udpPort`, `udpManualPort` | número | celular |
| `bluetoothAddress` | string | UI do PC |
| `cloudToken` | string | celular (não usado em lugar nenhum) |

- Todos os campos são obrigatórios na leitura; se o arquivo falhar ao parsear, o PC **recria vazio** (apaga pareamentos)
  (`PairedDevicesStorage.cpp:65-100`).
- `AddDevice` substitui entrada com mesmo `id` (mesmo celular+usuário) (`:50-56`).
- **Não existe** verificação de "pro", premium, licença, trial ou número máximo de dispositivos no código desktop (busca por
  `pro|premium|licen[cs]e|limit|max|trial|purchase|subscription` só encontrou textos de licença GPL e constantes de API).
  Limites reais: 10 conexões simultâneas nos servidores TCP (`PairingServer.cpp:19`, `TCPUnlockServer.cpp:14`); payload de
  frame ≤ 65535; JSON cifrado ≤ 1992 bytes / blob ≤ 2048 bytes (§2.6).

---

## 5. Versões e compatibilidade

- Protocolo de pareamento: `"4.0.0"` (`AppInfo.cpp:15-17`). Exigido **igual** em `PacketPairInit.protoVersion`
  (`PairingServer.cpp:152-153`), senão `errMsg` = "error_protocol_mismatch" (`en_US.json:14`).
- `CompareVersion` (`AppInfo.cpp:39-62`): ignora sufixo após `-`, compara números por `.`, parte ausente = 0 (`"4.0"` == `"4.0.0"`);
  **se algum pedaço não for numérico retorna 0 (= compatível)**.
- Protocolo de desbloqueio: `"3.0.0"` enviado em `UNLOCK_REQUEST.protoVersion` (`BaseUnlockConnection.cpp:129`, `AppInfo.cpp:19-21`).
  O PC não verifica versão do celular no desbloqueio; cabe ao celular responder `PROTOCOL_ERROR`.
- Versão do app: `3.3.4` (`AppInfo.cpp:9`); não é trafegada.

---

## 6. Checklist do cliente Android

- [ ] Parser de QR (novo com `serverId` / legado com `ip`) e do código Base32 com `-`.
- [ ] Cripto: PBKDF2-HMAC-SHA256 65535 it./32 B; AES-256-GCM IV 16 B, tag 16 B; blob `IV||salt||ct||tag`;
      prefixo `int64 BE ms`; checagem ±120 s; hex maiúsculo. (Dica: o PC deriva a chave do salt que vier, então o celular
      pode reutilizar um salt fixo por sessão e cachear a chave derivada para os próprios envios.)
- [ ] Framing: magic 8 B + ID u16 BE + len u16 BE + payload; leitor com ressincronização no magic.
- [ ] Descoberta de pareamento: UDP em `QR.port`, decifra, confere `serverId` (MulticastLock/Wi-Fi lock para receber broadcast).
- [ ] Pareamento TCP: enviar `0x50` com **todos** os campos e `protoVersion "4.0.0"`; ler `0x51`; tratar `errMsg`; persistir dados de §2.7.
- [ ] `deviceUUID` estável por instalação (re-parear deve gerar o mesmo `id`).
- [ ] UDP: escutar `udpPort` (serviço em primeiro plano) e `udpManualPort` (quando app aberto); filtrar `deviceId`; deduplicar;
      conectar `pcbuIP:pcbuPort`; enviar `0xB0`.
- [ ] TCP: servidor em `tcpPort`; ao aceitar, esperar `0xB1` direto.
- [ ] BT: servidor RFCOMM com UUID `62182bf7-97c8-45f9-aa2c-53c5f2008bdf`; mesmo fluxo do TCP.
- [ ] Ao receber `0xB1`: conferir `deviceId` pareado (senão `NOT_PAIRED`), `protoVersion` (senão `PROTOCOL_ERROR`), decifrar
      (timestamp → `TIME_ERROR`, falha → `DATA_ERROR`), mostrar `user`/`program`, biometria, responder `0xB2`
      (`CANCEL` se o usuário recusar).
- [ ] Tratar `CLOSE` cru / EOF como cancelamento do lado do PC.
- [ ] Wake-on-LAN opcional com `macAddresses`.
- [ ] Relógio: avisar o usuário se a diferença PC×celular passar de 2 min.

---

## 7. Questões em aberto

1. **Portas padrão do celular** (`tcpPort`, `udpPort`, `udpManualPort`): não definidas no desktop; valores do app oficial desconhecidos.
2. **Semântica de `isManual`** e se `udpPort` e `udpManualPort` devem ser diferentes: o PC só muda a porta de destino e a flag.
3. **Por que mudar `unlockServerPort` apaga dispositivos UDP** (`SettingsForm.qml:463-465`) se o broadcast já leva `pcbuPort`?
   Sugere que o app oficial usa o `unlockServerPort` guardado no pareamento (talvez no modo manual, conectando sem broadcast).
4. **Nuvem**: `cloudToken`, `CLOUD_TCP` e `CLOUD_BT` não têm implementação no desktop; enviar `cloudToken:""`.
5. **RFCOMM seguro vs inseguro** e nome do serviço SDP esperado: o PC não exige autenticação explícita no `connect`; o PC faz
   bonding via `BluetoothAuthenticateDevice` no pareamento (`BluetoothHelper.Win.cpp:77`). O `BTUnlockServer` (não usado) registra
   UUID diferente no Windows (`…8be0`, `BluetoothHelper.Win.cpp:106`) — irrelevante enquanto não for usado.
6. **IV GCM de 16 bytes no Android**: padrão, mas não verificado aqui se o provedor Conscrypt do aparelho aceita; alternativa BouncyCastle `GCMBlockCipher`.
7. **Timeout de socket inefetivo**: sockets do PC ficam não-bloqueantes (`TCPUnlockClient.cpp:79`, `PairingServer.cpp:112`) e o laço de
   leitura trata `WOULDBLOCK` como "tentar de novo" (`BaseConnection.cpp:140-156,177-178`), então `SO_RCVTIMEO` (120 s) aparentemente
   nunca dispara; após conectar, o PC espera indefinidamente até resposta ou cancelamento. O celular deve impor seu próprio timeout.
8. **Formato exato de `user` no Windows**: vem de `userDomain` do credential provider (`CUnlockCredential.cpp:54,95`); presumivelmente
   `DOMINIO\usuario`, não confirmado no código lido.
9. **`CompareVersion` permissivo**: versão não numérica passa na checagem de pareamento (`AppInfo.cpp:59-61`).
10. **Momento da biometria no modo UDP** (antes de conectar ou após `0xB1`): o PC aceita ambos, pois não tem timeout após conexão
    e o timestamp da resposta é gerado na hora da cifra.
11. **Regex de IP privado** aceita faixas 172.x mais amplas que 172.16/12 (`NetworkHelper.cpp:36-37`); impacto só na escolha de interfaces.

# NEXORA GBA Link — conexão e Link Cable

**Estado:** protocolo, transporte LAN, sala host/join e primeira ponte JNI/mGBA MULTI SIO estão integrados no branch unificado. Troca Pokémon real ainda não foi validada em dois aparelhos físicos.  
**Protocolo:** NXLP v1  
**Escopo atual:** dois NEXORAs na mesma rede Wi-Fi, GBA MULTI SIO de 16 bits.

## Como conectar

1. Os dois aparelhos precisam estar na mesma rede Wi-Fi.
2. Ambos devem carregar o mesmo jogo GBA, com o mesmo game code e CRC32 da ROM (mesma revisão).
3. No jogo, abrir o botão de menu ☰ e selecionar **Link Cable / Troca Pokémon**.
4. O anfitrião cria uma sala e compartilha o código de quatro dígitos e o IPv4/porta.
5. O segundo aparelho informa o endereço e o código para entrar.
6. Depois de conectado, abrir o Centro Pokémon e falar com a enfermeira no segundo andar para entrar no Cable Club.

A porta TCP padrão é 38271. O host aguarda até 120 segundos por um participante. O socket usa TCP_NODELAY, keepalive e buffers limitados. A leitura de rede ocorre em thread dedicada. O handshake valida sessão, player role, game code e CRC32 antes de aceitar a conexão.

## Arquitetura de transferência

- P0 (host) inicia a transferência GBA MULTI e envia um `SerialTransfer`.
- P1 (joiner) recebe a solicitação por uma fila JNI protegida, processada no começo do próximo `nativeRunFrame`.
- O driver agenda a conclusão local com os ciclos de transferência do mGBA, atualiza SIOMULTI/SIOCNT e emite IRQ SIO quando habilitada.
- P1 devolve um `SerialResult`.
- P0 recebe o resultado por uma fila nativa e completa o evento SIO por polling não bloqueante a cada 2048 ciclos GBA.
- Transferências têm ID crescente, filas limitadas e prazo máximo de quatro segundos. Falha de rede ou sequência inválida interrompe a sessão.

O callback SIO não executa I/O de socket nem espera em uma condition variable. Isso remove o bloqueio de vários segundos que existia na primeira versão do driver. Entretanto, o mecanismo atual ainda não é um lockstep distribuído determinístico: o core local pode avançar enquanto SIOCNT está BUSY. A precisão do comportamento deve ser validada no Cable Club em dois dispositivos.

## Modos SIO e escopo

| Modo | Uso | Estado atual |
|---|---|---|
| MULTI 16-bit | Link Cable GBA multiplayer | Primeiro driver remoto P0/P1 implementado, teste físico pendente |
| NORMAL 8-bit | Serial síncrono de 8 bits | Não implementado |
| NORMAL 32-bit | Serial síncrono de 32 bits | Não implementado |
| UART | Serial assíncrono | Não implementado |
| GPIO | Linhas seriais controladas por registradores | Não implementado |
| JOYBUS | Acessórios Joybus | Não implementado |
| RFU / Wireless Adapter | Comunicação sem fio própria de alguns jogos | Não implementado |

A primeira meta é Pokémon FireRed/LeafGreen via Cable Club. O código de sala é um identificador curto para uso na LAN, não uma credencial de segurança. A versão atual não inclui relay de internet, Bluetooth, descoberta automática ou quatro aparelhos.

## Protocolo NXLP v1

Mensagens definidas:

- `Hello` / `HelloAck`: negociação de sessão, jogador, quantidade máxima de participantes, game code e CRC32 da ROM.
- `SerialTransfer`: pedido serial com ID, ciclo de emulação, modo SIO, baud rate, largura e registradores.
- `SerialResult`: palavras recebidas ordenadas por ID de jogador e estado SIO.
- `Ping` / `Pong`: verificação de conexão.
- `Goodbye` / `ProtocolError`: encerramento e erros explícitos.

Todos os inteiros usam big-endian. O cabeçalho contém assinatura `NXLP`, versão, tipo e tamanho do payload. O decodificador incremental aceita quadros TCP fragmentados e vários quadros coalescidos, limita o tamanho dos dados e rejeita versões, tipos, assinaturas e comprimentos inválidos. O protocolo não envia ROMs nem arquivos de save.

## Testes automatizados

Os testes JVM cobrem:
- Identidade de sessão derivada do código de sala.
- Round-trip de Hello, transferências seriais e resultados.
- Fragmentação e coalescência de quadros.
- Rejeição de assinatura, versão e tamanho de payload inválidos.
- Parse e validação do endereço/porta.
- Handshake host/join local, troca de mensagens e rejeição de game code/CRC incompatíveis.

O CI do branch unificado foi atualizado para executar `:app:testDebugUnitTest` além da compilação Android. Os resultados do novo branch ainda precisam ser confirmados; não assumir que o build está verde antes de inspecionar os logs.

## Critério de conclusão

A compilação e os testes de protocolo não comprovam uma troca Pokémon. O aceite final exige dois celulares Android instalando a mesma versão, carregando a mesma ROM FireRed/revisão, criando e entrando na sala, chegando ao Cable Club, concluindo e confirmando uma troca, e verificando os saves dos dois jogos.

Até esse teste, o recurso deve ser descrito como **integração experimental de Link Cable em validação**, não como troca Pokémon pronta.

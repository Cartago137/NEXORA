# NEXORA GBA Link — conexão e Link Cable

**Estado:** primeira integração Android/JNI/SIO e salas LAN de dois aparelhos compilam; troca Pokémon real ainda não foi validada em dispositivos físicos.  
**Protocolo:** NXLP v1  
**Escopo desta implementação:** dois NEXORAs na mesma rede Wi-Fi, usando o modo GBA MULTI SIO.

## Resultado desta etapa

O NEXORA já oferece uma interface de sala dentro da tela do jogo. O anfitrião cria uma sala e recebe um código de quatro dígitos e o endereço IPv4 local. O segundo aparelho informa IP e código. O código é convertido em um identificador de sessão compartilhado; o handshake verifica o identificador, o game code e o CRC32 da ROM antes de aceitar a conexão.

A porta TCP padrão é 38271. O anfitrião aguarda até 120 segundos por um participante. O socket usa TCP_NODELAY, keepalive e buffers limitados; a leitura ocorre em thread dedicada. A camada Kotlin transporta eventos NXLP e não executa diretamente callbacks de emulação.

O JNI liga o transporte a um primeiro driver `GBASIODriver` instalado no modo `SIO_MULTI` do core mGBA ativo. P0 (host) inicia a transferência e publica `SIOMLT_SEND`; P1 (client) recebe a solicitação, agenda o evento de transferência no relógio de emulação e responde com sua palavra de 16 bits. Os dois lados preenchem `SIOMULTI0..3`, atualizam os bits de estado de `SIOCNT`, limpam BUSY e geram a IRQ SIO quando habilitada.

O leitor de rede não adquire o mutex do core. O resultado serial chega pela fila protegida por mutex/condição; o callback do evento de P0 espera a resposta por um tempo limitado, sem realizar I/O de socket. O agendamento usa a tabela de ciclos do mGBA para o número de participantes.

## Como o mGBA organiza o Link Cable

O GBA expõe vários modos SIO, e o mGBA os despacha por meio de `GBASIODriver`:

| Modo SIO | Finalidade | Estado no NEXORA |
|---|---|---|
| NORMAL 8-bit | transferência serial síncrona de 8 bits | Não implementado pelo driver remoto |
| NORMAL 32-bit | transferência serial síncrona de 32 bits | Não implementado pelo driver remoto |
| MULTI | cabo multiplayer, com palavras de 16 bits e IDs de jogador | Primeiro caminho remoto implementado (P0/P1) |
| UART | comunicação serial assíncrona | Não implementado |
| GPIO | linhas seriais controladas por registradores | Não implementado |
| JOYBUS | interface de acessórios Joybus | Não implementado |

Para Pokémon FireRed/LeafGreen, a meta inicial é o fluxo de Link Cable do Cable Club, que usa o caminho multiplayer SIO. Outros protocolos de comunicação e acessórios não devem ser considerados compatíveis por esta implementação.

O mGBA também possui um mecanismo de lockstep e nós locais para coordenar vários cores na mesma máquina. Isso é diferente de simplesmente enviar pacotes de rede: a sincronização local mantém as transferências alinhadas nos pontos certos do relógio emulado.

## Estratégia de rede e limitações conhecidas

Há duas arquiteturas importantes para multiplayer:

1. **SIO remoto direto:** envia pedidos e respostas serial pela rede. É a primeira prova de integração usada nesta branch. É mais simples para ligar o Cable Club ao transporte existente, mas a resposta da rede pode atrasar o callback de transferência de P0 e produzir pausas. A sessão atual também não executa uma sincronização determinística de cada quadro.
2. **Par replicado:** cada aparelho mantém duas instâncias locais do jogo, uma para cada jogador; os aparelhos sincronizam entradas por quadro e verificam os estados. Um projeto experimental independente baseado em mGBA demonstrou viabilidade em Android ARM64 e sessões prolongadas em dois aparelhos. Essa arquitetura é uma referência para a evolução de estabilidade/latência, mas ainda não foi portada para o NEXORA.

A implementação atual não é um driver universal de cabo. Não inclui UART, NORMAL 8/32, GPIO, Joybus, RFU/Wireless Adapter, quatro dispositivos, descoberta automática, Bluetooth ou relay de internet. O código de sala é um identificador curto para uso na LAN, não uma credencial de segurança.

## Protocolo NXLP v1

Mensagens definidas:

- `Hello` / `HelloAck`: negociação de sessão, jogador, quantidade máxima de participantes, código do jogo e CRC32 da ROM.
- `SerialTransfer`: pedido serial com ID de transferência, ciclo de emulação, modo SIO, baud rate, largura e registradores.
- `SerialResult`: palavras recebidas ordenadas por ID de jogador e estado SIO.
- `Ping` / `Pong`: medição de latência e detecção de conexão inativa.
- `Goodbye` / `ProtocolError`: encerramento e erros explícitos.

Todos os inteiros usam big-endian. O cabeçalho contém a assinatura `NXLP`, versão, tipo e tamanho do payload. O decodificador incremental lida com leituras TCP fragmentadas e vários quadros recebidos juntos, limita o tamanho dos dados e rejeita versões, tipos e comprimentos inválidos. O protocolo não envia ROMs nem arquivos de save.

## Validação

No workflow Android run 229, os testes JVM passaram e o APK debug e o APK release assinado foram gerados. Os testes de protocolo cobrem round-trip, fragmentação, mensagens coalescidas, ordem das palavras seriais e identidade determinística do código da sala.

Isso confirma compilação e testes automatizados da camada Kotlin. **Não comprova uma troca Pokémon real.** A validação final exige instalar a mesma versão em dois celulares, carregar a mesma ROM nos dois, criar/entrar na sala, falar com a enfermeira no segundo andar do Centro Pokémon e concluir uma troca no Cable Club.

## Próximas etapas

1. Validar conexão host/join em dois dispositivos físicos e medir pausas/tempo das transferências.
2. Corrigir diferenças de ciclo/estado que os testes de Cable Club revelarem, sem falsificar uma conclusão de transferência.
3. Validar troca Pokémon completa em FireRed/LeafGreen e só então declarar o recurso funcional.
4. Migrar o runtime para sincronização replicada por quadro se a latência do SIO remoto prejudicar a experiência.
5. Expandir o suporte para outros modos SIO, mais jogadores e transportes adicionais depois da primeira troca validada.

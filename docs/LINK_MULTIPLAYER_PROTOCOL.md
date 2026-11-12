# NEXORA GBA Link — protocolo inicial

**Estado:** codec e transporte TCP local por Wi-Fi para dois aparelhos implementados; integração com o core e interface ainda não implementadas.  
**Versão do protocolo:** 1  
**Escopo inicial:** duas instâncias GBA do NEXORA na mesma rede Wi-Fi. O formato reserva IDs para até quatro jogadores.

## O que esta etapa entrega

`WifiLinkTransport.kt` implementa o primeiro transporte direto na mesma rede Wi-Fi (porta TCP padrão 38271; o host aguarda um participante por até 30 segundos): um aparelho abre um socket TCP, o outro conecta usando endereço/porta e ID de sessão, e ambos validam código do jogo e CRC32 antes de aceitar a sessão. Há timeout de conexão/handshake, TCP_NODELAY, keepalive, leitura em thread dedicada, escrita sincronizada e encerramento explícito. A permissão Android `INTERNET` foi adicionada para permitir sockets locais.

Os testes de transporte exercitam dois peers locais, handshake, troca de mensagens e rejeição de ROM incompatível. A ponte JNI também expõe o código de jogo GBA e o CRC32 da ROM ativa para alimentar essa validação sem depender do nome do arquivo.

`LinkProtocol.kt` define mensagens binárias versionadas e um decodificador incremental independente de transporte. O decodificador lida com leituras TCP fragmentadas e com vários quadros recebidos juntos, limita o tamanho dos dados e rejeita versões, tipos e comprimentos inválidos.

Mensagens definidas:

- `Hello` / `HelloAck`: negociação de sessão, jogador, quantidade máxima de participantes, código do jogo e CRC32 da ROM.
- `SerialTransfer`: pedido serial com ID de transferência, ciclo de emulação, modo SIO, baud rate, largura e registradores.
- `SerialResult`: palavras recebidas ordenadas por ID de jogador e estado SIO.
- `Ping` / `Pong`: medição de latência e detecção de conexão inativa.
- `Goodbye` / `ProtocolError`: encerramento e erros explícitos.

Todos os inteiros usam big-endian. O cabeçalho contém a assinatura `NXLP`, versão, tipo e tamanho do payload. O protocolo não envia ROMs nem arquivos de save.

## Próximas etapas obrigatórias

1. Adicionar testes unitários de round-trip, fragmentação TCP, múltiplos quadros, payload truncado, versão desconhecida e limites.
2. Integrar o transporte Wi-Fi a uma interface de sala/conexão e acrescentar cancelamento de espera do host.
3. Implementar adaptador nativo do driver SIO do mGBA. O caminho de emulação precisa evitar bloquear o thread de frames esperando a rede; transferências devem ser coordenadas por ciclo e ter timeout/erro recuperável.
4. Integrar o estado de sessão à UI sem iniciar sockets automaticamente.
5. Testar com duas cópias compatíveis do mesmo jogo, começando pela comunicação serial de Pokémon. Só depois declarar troca ou batalha funcional.
6. Expandir para quatro jogadores e avaliar Bluetooth e internet como transportes adicionais.

## Limites deliberados

- O transporte Wi-Fi já estabelece uma conexão TCP local entre dois aparelhos e troca mensagens do protocolo; esta etapa **ainda não** conecta o driver serial ao mGBA nem realiza trocas Pokémon.
- Um `HelloAck` positivo apenas confirma negociação de protocolo; não prova compatibilidade de jogo nem funcionamento de Link Cable.
- CRC32 e código do jogo são identificadores de compatibilidade, não mecanismos de segurança. A camada de sessão ainda deverá validar peers e tratar dados como não confiáveis.
- O transporte de internet exigirá uma estratégia de encaminhamento/relay ou conexão direta com travessia de NAT; não está incluído nesta etapa.

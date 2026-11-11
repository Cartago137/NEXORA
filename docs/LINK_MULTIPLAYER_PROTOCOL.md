# NEXORA GBA Link — protocolo inicial

**Estado:** fundação de protocolo implementada; transporte e integração com o core ainda não implementados.  
**Versão do protocolo:** 1  
**Escopo inicial:** duas instâncias GBA do NEXORA na mesma rede Wi-Fi. O formato reserva IDs para até quatro jogadores.

## O que esta etapa entrega

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
2. Implementar transporte Wi-Fi com descoberta/conexão explícita, timeout, cancelamento e validação da sessão.
3. Implementar adaptador nativo do driver SIO do mGBA. O caminho de emulação precisa evitar bloquear o thread de frames esperando a rede; transferências devem ser coordenadas por ciclo e ter timeout/erro recuperável.
4. Integrar o estado de sessão à UI sem iniciar sockets automaticamente.
5. Testar com duas cópias compatíveis do mesmo jogo, começando pela comunicação serial de Pokémon. Só depois declarar troca ou batalha funcional.
6. Expandir para quatro jogadores e avaliar Bluetooth e internet como transportes adicionais.

## Limites deliberados

- Esta etapa **não** cria uma conexão de rede e **não** conecta o driver serial ao mGBA.
- Um `HelloAck` positivo apenas confirma negociação de protocolo; não prova compatibilidade de jogo nem funcionamento de Link Cable.
- CRC32 e código do jogo são identificadores de compatibilidade, não mecanismos de segurança. A camada de sessão ainda deverá validar peers e tratar dados como não confiáveis.
- O transporte de internet exigirá uma estratégia de encaminhamento/relay ou conexão direta com travessia de NAT; não está incluído nesta etapa.

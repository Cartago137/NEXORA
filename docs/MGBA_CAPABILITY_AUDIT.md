# NEXORA — Auditoria de capacidades do mGBA

**Data da auditoria:** 2026-11-11  
**Repositório:** `Cartago137/NEXORA`  
**Snapshot da branch principal:** `03c18c0a4f950026b87f4d52cc3125fed4edd250`  
**Core fixado:** mGBA 0.10.5 — `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`

## 1. Objetivo e método

Este documento compara as capacidades do mGBA 0.10.5 com o que está efetivamente conectado ao aplicativo Android do NEXORA no snapshot indicado. A presença de uma API no mGBA não significa que a funcionalidade esteja disponível no aplicativo.

Classificações:

- **Integrado:** existe caminho de código no NEXORA que conecta a funcionalidade ao aplicativo.
- **Parcial:** existe uma parte do caminho, mas falta integração, gerenciamento, interface ou validação.
- **Disponível no core, não integrado:** o mGBA oferece a capacidade, mas o NEXORA ainda não a expõe.
- **Desabilitado/não compilado:** a configuração atual não inclui esse componente.
- **Não suportado nessa versão:** a versão fixada não oferece suporte suficiente para o recurso pretendido.
- **Camada própria NEXORA:** é responsabilidade do aplicativo, não do núcleo.

## 2. Resumo executivo

O NEXORA já integra o núcleo GBA do mGBA por CMake e JNI. O caminho principal cobre carregamento de ROM, execução de frames, vídeo 240×160, áudio estéreo, entrada de botões e carregamento do save de bateria.

A integração ainda é estreita: o contrato JNI não expõe Save States, cheats, patches, sensores, rumble, Link Cable, Rewind ou scripts. A configuração CMake compila somente o core GBA, desabilita o core GB e desliga scripting.

**Importante sobre branches:** a branch principal auditada é `main`. O PR #16 (`ui/touch-controls-layout`) continua aberto em rascunho e não está incorporado à branch principal. Ele contém trabalho adicional de Fast Forward, ajustes de áudio e layout horizontal. Portanto, mudanças existentes apenas nesse PR não são contabilizadas como integradas em `main`.

## 3. Inventário de recursos

| Capacidade | mGBA 0.10.5 | Estado em `main` | Observação |
|---|---|---|---|
| Emulação GBA | Suportada | **Integrado** | `M_CORE_GBA=ON`; core criado como `mPLATFORM_GBA`. |
| Game Boy / Game Boy Color | Suportados pelo mGBA | **Desabilitado** | `M_CORE_GB=OFF`; requer habilitar e testar o core GB. |
| Carregamento de ROM GBA | API disponível | **Integrado** | URI é copiada para cache local; validação de tamanho e cabeçalho antes de carregar. |
| Execução de frames | API disponível | **Integrado** | JNI chama `runFrame`; saída fixa de 240×160 pixels. |
| Vídeo e framebuffer | API disponível | **Integrado** | Buffer nativo e conversão para ARGB Android; ainda não há pipeline de shaders configurável. |
| Áudio estéreo | API disponível | **Integrado** | Dois canais blip, PCM estéreo e AudioTrack. |
| Fast Forward | Suportado no frontend mGBA | **Não integrado em main / parcial no PR #16** | PR #16 implementa velocidades aceleradas no frontend Android; precisa de validação física do áudio após os ajustes. |
| Save normal / battery save | API disponível | **Parcial** | JNI expõe carregamento do arquivo por jogo. A persistência foi confirmada pelo usuário no APK testado; o contrato JNI atual não expõe uma operação explícita de gravação. Investigar o ciclo de flush/destruição do core antes de refatorar. |
| Save States | API disponível (`mCoreSaveStateNamed`, `mCoreLoadStateNamed`) | **Disponível no core, não integrado** | Não há métodos JNI nem interface de slots no aplicativo. |
| Rewind | Infraestrutura disponível no mGBA | **Disponível no core, não integrado** | Requer avaliar API, memória, snapshots e desempenho Android. |
| Cheats | Suporte nativo | **Disponível no core, não integrado** | Sem API JNI, persistência de conjuntos ou UI no app. |
| Patches IPS / UPS / BPS | Suporte no ecossistema mGBA | **Não integrado** | Importação atual aceita `.gba`, `.agb` e `.bin`; não há fluxo de patch na UI. |
| ZIP / 7z | VFS do mGBA pode suportar arquivos compactados | **Não integrado na Biblioteca** | O seletor e a validação atual não aceitam extensões ZIP/7z. Exige desenho seguro para leitura e identificação de ROMs dentro de arquivos. |
| RTC | Suportado pelo core GBA | **Parcial / não exposto** | Não existe configuração explícita de relógio ou opção de usuário no JNI. Verificar comportamento padrão do core e persistência em Save States. |
| Sensor solar | Suportado pelo mGBA para cartuchos compatíveis | **Disponível no core, não integrado** | Nenhuma leitura de sensor Android chega ao core. |
| Inclinação / rotação | Suportado pelo mGBA | **Disponível no core, não integrado** | Nenhum sensor Android ou fonte de rotação conectado ao JNI. |
| Rumble | Suportado pelo mGBA em hardware compatível | **Disponível no core, não integrado** | Não há callback JNI para acionar vibração Android. |
| Link Cable local | Suporte parcial no mGBA | **Disponível no core, não integrado** | Não há segundo core, driver de link ou sessão de comunicação no aplicativo. |
| Multiplayer Wi-Fi / internet | Não é uma solução pronta na versão fixada | **Camada própria NEXORA** | Será necessário transportar e sincronizar operações seriais, com controle de latência e reconexão. |
| Game Boy Printer / Camera | Suporte parcial para periféricos GB | **Desabilitado / não integrado** | Além de habilitar GB, requer interfaces próprias para periféricos. |
| BattleChip Gate | Suporte mGBA | **Disponível no core, não integrado** | Recurso especializado; avaliar demanda antes de priorizar. |
| Lua scripting | Suporte preliminar no mGBA 0.10.x | **Desabilitado** | `ENABLE_SCRIPTING=OFF` no CMake. Requer dependências e revisão de segurança/permissões antes de habilitar. |
| Depurador / acesso à memória | APIs e ferramentas mGBA | **Não integrado** | Não há interface de depuração no aplicativo. Pode ser ferramenta interna de diagnóstico, não recurso padrão. |
| Capturas / gravação de vídeo | Disponibilidade depende do frontend e dependências opcionais | **Não integrado** | Sem fluxo de captura no app; gravação pode exigir FFmpeg ou componentes adicionais. |

## 4. Integração nativa observada

### 4.1 JNI exposto atualmente

O arquivo `NativeBridge.kt` declara:

- `nativeStatus`
- `nativeLoadRom`
- `nativeRunFrame`
- `nativeLoadBatterySave`
- `nativeGetAudioSampleRate`
- `nativeReadAudio`
- `nativeClearAudio`
- `nativeSetKeys`
- `nativeAddKeys`
- `nativeClearKeys`
- `nativeUnloadRom`

Não foram encontrados métodos JNI para Save State, Rewind, cheats, patches, sensores, rumble, link, RTC configurável ou scripting.

### 4.2 Configuração CMake

A configuração atual:

- habilita `M_CORE_GBA`;
- desabilita `M_CORE_GB`;
- desabilita dependências opcionais;
- desabilita scripting;
- não compila os frontends Qt/SDL nem ferramentas de exemplo;
- fixa o commit upstream indicado no cabeçalho deste documento.

Essa escolha é adequada para manter o primeiro APK focado em GBA e reduzir dependências. Não deve ser interpretada como prova de que os recursos desabilitados são impossíveis; significa que ainda não foram incluídos na compilação e integração do NEXORA.

## 5. Estado da aplicação Android observado

### Biblioteca

Já existe importação múltipla de arquivos GBA, persistência local da lista, prevenção de duplicatas e opção para remover uma entrada sem apagar o arquivo original ou solicitar a exclusão do original. O armazenamento da Biblioteca ainda é simples (JSON em SharedPreferences).

Ainda não há implementação encontrada para busca, pastas personalizadas, favoritos, itens fixados, seleção múltipla ou histórico real de partidas. A tela Histórico presente no código principal é atualmente um estado vazio/placeholder.

### Controles

Há controles touch básicos e mapeamento de botões GBA, com operações separadas para adicionar e liberar bits de entrada. A personalização livre de posições e perfis não está implementada.

O layout horizontal mais recente do PR #16 deve ser avaliado separadamente, pois o PR ainda não foi incorporado a `main`.

### Saves

O carregamento do save de bateria é conectado após a ROM ser carregada. O usuário já validou que o save normal permanece após fechar e reabrir o aplicativo no APK de referência. Esse comportamento deve ser tratado como requisito de regressão.

Save States permanecem ausentes do código Kotlin/JNI da branch principal. A implementação deve usar a serialização do mGBA, mas manter arquivos, rotação de backups, recuperação, exportação e interface sob responsabilidade do NEXORA.

## 6. Arquitetura recomendada para aproveitar o mGBA

1. **Manter o JNI estreito:** não permitir que a UI Compose dependa de estruturas ou tipos internos do mGBA.
2. **Criar um contrato de core:** ciclo de vida, ROM, frame, áudio, entrada, saves, capacidades e erros.
3. **Adicionar relatório de capacidades:** o core GBA declara quais recursos suporta; a interface só mostra opções compatíveis.
4. **Separar serviços Android:** áudio, sensores, vibração, armazenamento e rede devem ser adaptadores do aplicativo.
5. **Isolar cada sistema:** GB/GBC ou futuros consoles devem entrar como módulos próprios, sem espalhar condicionais de console pela UI.
6. **Preservar o save normal:** implementar Save States sem substituir nem quebrar o arquivo de bateria já validado.
7. **Testar antes de anunciar:** sensores, rumble, link, Rewind e multiplayer precisam de testes por jogo e dispositivo.

## 7. Ordem sugerida de implementação

| Ordem | Entrega | Dependências / critério |
|---|---|---|
| 1 | Consolidar áudio e Fast Forward | Concluir PR #16, compilar e validar no aparelho em 1×, 1.5×, 2× e 3×. |
| 2 | Save States locais | JNI para salvar/carregar; cinco slots, confirmação de sobrescrita, gravação atômica e backups. |
| 3 | Autosave e recuperação | Checkpoint a cada 10 minutos, pausa/saída, recuperação após encerramento inesperado e testes de corrupção. |
| 4 | RTC e saves | Verificar consistência de RTC e dados de bateria entre sessões e estados. |
| 5 | Sensores e rumble | Adaptadores Android, calibração, permissões e fallback quando hardware não existir. |
| 6 | Cheats e patches | API nativa, armazenamento por jogo, importação e validação. |
| 7 | Rewind | Medir RAM, custo de snapshots e impacto em aparelhos de menor desempenho. |
| 8 | Link local e Wi-Fi | Primeiro duas instâncias locais; depois transporte de rede, sincronização e recuperação de desconexão. |
| 9 | GB/GBC | Habilitar `M_CORE_GB`, ampliar detecção de plataforma, entrada, proporção de tela, saves e testes. |
| 10 | Ferramentas avançadas | Depuração, scripting e captura somente após definir limites de segurança e uso. |

## 8. Pontos de atenção e riscos

- **Save State não é battery save:** manter os dois mecanismos separados e definir como a restauração sincroniza dados de cartucho e RTC.
- **Atomicidade:** nunca sobrescrever um slot válido antes de confirmar que o novo estado foi escrito e validado.
- **ROMs compactadas:** tratar arquivos importados como dados não confiáveis; limitar tamanho descompactado e evitar extração indiscriminada.
- **Multiplayer:** Link Cable parcial no core não equivale a multiplayer Wi-Fi pronto. A sincronização temporal é o desafio principal.
- **Scripting:** Lua aumenta flexibilidade, mas scripts de terceiros precisam de isolamento e limites de acesso.
- **Licenças:** preservar notices do mGBA e dependências; revisar obrigações antes de redistribuir binários.
- **Compatibilidade:** não prometer suporte universal a periféricos ou jogos sem uma matriz de testes.

## 9. Referências

- [mGBA 0.10.5 — notas oficiais da versão](https://mgba.io/2025/03/08/mgba-0.10.5/)
- [FAQ oficial — compatibilidade e periféricos](https://mgba.io/faq.html)
- [Documentação oficial da API de scripting](https://mgba.io/docs/scripting.html)
- [Commit mGBA fixado pelo NEXORA](https://github.com/mgba-emu/mgba/commit/26b7884bc25a5933960f3cdcd98bac1ae14d42e2)
- [PR #16 — controles touch, Fast Forward e otimizações](https://github.com/Cartago137/NEXORA/pull/16)

---

**Nota de escopo:** esta auditoria é uma inspeção estática do código-fonte e da configuração do repositório no snapshot informado. Não substitui testes em aparelho físico. Onde o comportamento depende de ciclo de vida interno do mGBA ou de hardware Android, o documento registra a necessidade de validação em vez de presumir funcionamento.

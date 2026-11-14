# NEXORA — Central de Controle de IA

Interface web inicial, independente do aplicativo Android. Pode ser aberta em navegador de celular ou computador.

## Estado desta versão

- Interface responsiva em português.
- Visão geral de provedores, tarefas, atividade e custos.
- OpenRouter, Groq e Gemini aparecem como integrações planejadas.
- Ainda não existe backend, autenticação, conexão com provedores ou execução de tarefas.
- Nenhuma chave de API é solicitada ou armazenada nesta interface.

## Abrir localmente

Abra `index.html` em um navegador. Para publicar, hospede o diretório como site estático (por exemplo, GitHub Pages) depois de configurar o domínio/origem e as permissões de publicação.

## Próximas etapas

1. Definir autenticação e hospedagem protegida do painel.
2. Criar backend para ler apenas configurações e executar chamadas autorizadas.
3. Conectar primeiro o OpenRouter usando segredo do GitHub Actions ou um gerenciador de segredos do backend — nunca código do navegador.
4. Registrar tarefas e uso sem expor chaves.
5. Adicionar Groq e Gemini como provedores opcionais.
6. Só depois habilitar propostas de alteração em branches isoladas e pull requests para revisão humana.

## Segurança planejada

- Sem chaves no HTML, JavaScript, repositório ou armazenamento do navegador.
- Provedores pagos desativados por padrão.
- Limites de orçamento e registro de uso antes de habilitar chamadas pagas.
- Modelos não poderão fazer merge nem publicar alterações automaticamente.
- Toda alteração de código deverá passar por revisão humana.

**Aviso:** esta é apenas a primeira camada visual. Não é ainda um painel operacional conectado.
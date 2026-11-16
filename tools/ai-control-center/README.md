# NEXORA — Central de Controle de IA

Painel web privado e independente do aplicativo Android, acessível por navegador em celular e computador.

## Estado atual

- Interface responsiva em português com login administrativo.
- Sessão protegida por cookie HttpOnly e SameSite=Strict.
- API e interface servidas na mesma origem.
- Chat conectado à rota protegida `POST /api/chat`.
- Roteamento selecionável no servidor para OpenRouter (`openrouter/free`) e Groq (`openai/gpt-oss-20b`).
- Modelos e endpoints são definidos no backend; o navegador não pode substituir essas escolhas.
- Indicadores mostram quais chaves estão configuradas, sem expor credenciais.
- Limites de tamanho/histórico e frequência de chamadas.
- Armazenamento de sessão e limites com Upstash Redis REST quando configurado.
- Modo local de desenvolvimento usa memória; o servidor recusa iniciar em `NODE_ENV=production` sem Redis persistente.
- Workflow automatizado testa autenticação, chat e armazenamento persistente simulado.
- Render Blueprint de pré-visualização preparado, com deploy automático desativado.
- Gemini foi desativado após falhas persistentes; sua implementação permanece preservada, mas não aparece como provedor ativo nem aceita chamadas.

## Serviços e custos previstos

- **Render Free:** hospeda o servidor para testes sem custo de compute, sujeito às cotas e limitações do plano. O serviço gratuito adormece após 15 minutos sem tráfego, demora aproximadamente um minuto para acordar e usa filesystem efêmero. Não é indicado como hospedagem de produção.
- **Upstash Redis Free:** armazena sessões e limites fora do processo do servidor. O plano gratuito publicado inclui 256 MB, 500 mil comandos mensais e 10 GB de banda. É necessário acompanhar os limites da conta.
- **OpenRouter:** o chat usa `openrouter/free`.
- **Groq:** usa `openai/gpt-oss-20b` via API compatível com OpenAI.

As chaves dos dois provedores ativos são opcionais e devem ser configuradas somente no ambiente do servidor. Groq pode gerar custos conforme plano, cotas e faturamento da conta; confirme as condições no painel antes de ativar a chave. A Central não chama nenhum provedor automaticamente.

Nenhum serviço foi criado, nenhuma conta conectada e nenhum segredo configurado. Os planos e limites podem mudar; confirme-os no painel dos provedores antes de cadastrar recursos.

## Integração com clientes MCP

A Central expõe `POST /mcp` para clientes compatíveis com Model Context Protocol via HTTP. A ferramenta `nexora_chat` permite enviar prompts somente ao OpenRouter ou Groq. A rota exige um segredo independente `NEXORA_MCP_TOKEN` (Bearer, mínimo de 32 caracteres), não concede acesso administrativo e compartilha o limite de chamadas do chat. Configure o segredo diretamente no Render e conecte o cliente à URL HTTPS do serviço com autenticação Bearer. Não compartilhe o token na conversa nem o registre no repositório. Para clientes desktop compatíveis, a conexão pode ser adicionada em ChatGPT desktop > Settings > MCP servers, usando transporte Streamable HTTP, a URL `https://nexora-ai-control-center-preview.onrender.com/mcp` e autenticação Bearer. A configuração MCP do desktop é separada do aplicativo móvel; o app Android não oferece a tela de cadastro de servidores MCP.

## Requisitos

- Node.js 20 ou superior.
- Senha administrativa exclusiva com pelo menos 16 caracteres.
- Redis REST compatível com Upstash para qualquer execução em produção.
- Chave OpenRouter configurada apenas no ambiente do servidor para habilitar respostas reais.

## Executar localmente

1. Copie `.env.example` para `.env`.
2. Defina `NEXORA_ADMIN_PASSWORD` com uma senha forte e exclusiva.
3. Para testar o chat, configure `OPENROUTER_API_KEY` no ambiente local, nunca no navegador ou no Git.
4. Para testar o armazenamento persistente, configure também `UPSTASH_REDIS_REST_URL` e `UPSTASH_REDIS_REST_TOKEN`.

```sh
cd tools/ai-control-center/server
npm test
npm start
```

O servidor local usa `127.0.0.1:8787`. O modo local sem Redis usa memória e não deve ser publicado.

## Preparação de hospedagem Render

O arquivo `render.yaml` descreve um serviço web gratuito ligado à branch `feature/ai-control-center`. O deploy automático está desligado. As chaves dos provedores ativos e o token MCP estão declarados como segredos opcionais (`sync: false`); esse modo solicita valores no primeiro provisionamento do Blueprint, mas não injeta segredos em ambientes de preview e não atualiza serviços existentes em sincronizações posteriores. A configuração:

- executa `npm test` durante o build;
- usa `npm start` para iniciar o servidor;
- define `NODE_ENV=production` e `HOST=0.0.0.0`;
- exige que a senha administrativa e as credenciais Upstash sejam preenchidas como segredos no painel;
- não inclui uma chave OpenRouter. Ela poderá ser adicionada depois, se e quando a integração for autorizada.

Para uma implantação já existente, adicione manualmente `OPENROUTER_API_KEY`, `GROQ_API_KEY` e `NEXORA_MCP_TOKEN` em Environment no painel do Render; salvar as variáveis requer novo deploy para entrarem em vigor. O serviço Render gratuito é acessível pela internet; a tela de login protege o painel, mas não equivale a uma rede privada. O endereço não deve ser divulgado. O HTTPS é fornecido pelo Render. Não sincronize nem crie o Blueprint até que o armazenamento Redis e os segredos tenham sido preparados.

## Segurança e limites conhecidos

- Segredos ficam em variáveis de ambiente, nunca no código do navegador ou no Git.
- O navegador não recebe a chave do provedor.
- O identificador aleatório da sessão é enviado em cookie HttpOnly; somente o hash é armazenado no Redis.
- O Redis guarda as sessões com expiração de 12 horas e aplica contadores compartilhados de limite de acesso.
- Em produção, o cookie recebe Secure e o servidor exige Redis via HTTPS.
- Requisições de escrita verificam a origem; o cookie usa SameSite=Strict.
- O chat aceita somente mensagens user/assistant, até 20 mensagens e 12.000 caracteres por mensagem.
- Respostas limitadas a 1.024 tokens; o servidor usa modelos fixos por provedor e não aceita modelo informado pelo navegador.
- Se o Redis ficar indisponível, a API responde com erro temporário em vez de ignorar silenciosamente o armazenamento persistente.
- Não há MFA, recuperação de senha, contas individuais nem trilha de auditoria durável.
- O plano gratuito do Render pode reiniciar o serviço e não oferece disco persistente. Upstash também possui cotas gratuitas.
- Os testes simulam Redis e OpenRouter; não fazem chamadas reais nem geram cobranças.

## Próximas etapas

1. Validar os novos testes de Redis e a compilação Android.
2. Criar o banco Redis gratuito e preencher seus segredos no provedor de hospedagem.
3. Criar o Blueprint Render manualmente, sem deploy automático.
4. Fazer um deploy privado de teste e validar login, sessão, HTTPS e chat no celular.
5. Habilitar somente as chaves dos provedores que forem autorizados, após revisar cotas e faturamento.
6. Testar cada integração no ambiente hospedado e acompanhar os limites de uso.

**Importante:** não envie senhas ou chaves nesta conversa. O projeto ainda não está hospedado nem pronto para uso público.

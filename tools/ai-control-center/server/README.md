# NEXORA AI Control Center — servidor e provedores

O backend serve a interface e a API na mesma origem. A Central oferece roteamento selecionável para OpenRouter e Groq; cada credencial permanece exclusivamente no ambiente do servidor.

## Arquitetura

- Servidor Node.js sem dependências externas.
- Login administrativo com senha `NEXORA_ADMIN_PASSWORD` (mínimo de 16 caracteres).
- Sessão aleatória em cookie HttpOnly, SameSite=Strict, validade de 12 horas; somente o hash da sessão é persistido.
- Em produção, cookie Secure e Redis REST via HTTPS obrigatórios.
- Verificação de origem para requisições de escrita, limite de tentativas de login e encerramento de sessão.
- `GET /api/status` informa quais chaves estão configuradas sem retornar qualquer segredo.
- `POST /api/chat` aceita `provider` opcional (`openrouter` ou `groq`); o servidor escolhe o endpoint e modelo fixos. O cliente nunca pode escolher endpoint, modelo ou chave.
- OpenRouter usa `openrouter/free`; Groq usa `openai/gpt-oss-20b`. Gemini foi removido do código ativo após falhas persistentes.
- Groq usa formato de chat compatível com OpenAI. 
- Limites: 1.024 tokens de saída, 20 mensagens, 12.000 caracteres por mensagem e 10 solicitações por minuto por endereço.
- Falhas dos provedores registram metadados técnicos seguros (provedor, modelo, status HTTP ou código de transporte), sem registrar chaves, cabeçalhos ou conteúdo das mensagens.
- Testes automatizados simulam os provedores e o Redis; não fazem chamadas externas nem geram cobranças.

## Integração externa via MCP

O endpoint `POST /mcp` implementa o transporte HTTP Streamable do Model Context Protocol (versão `2025-03-26`) sem estado de sessão e responde em JSON. Ele exige `Authorization: Bearer <NEXORA_MCP_TOKEN>`, com token aleatório de pelo menos 32 caracteres, configurado somente no ambiente do servidor.

A ferramenta publicada é `nexora_chat`: recebe um prompt e encaminha para OpenRouter ou Groq, usando modelos e endpoints fixos no backend. Ela não oferece acesso ao shell, arquivos, segredos, deploys ou administração do servidor. O limite de chamadas é compartilhado com o chat do painel. Requisições MCP não autenticadas são rejeitadas. A ferramenta retorna apenas texto da resposta e metadados do provedor; não aceita modelo, endpoint ou cabeçalho definidos pelo cliente.

Para conectar um cliente MCP remoto, use a URL HTTPS do serviço terminada em `/mcp` e configure autenticação Bearer com o valor de `NEXORA_MCP_TOKEN`. O token deve ser criado e inserido diretamente no gerenciador de segredos do Render; nunca o envie pelo chat ou coloque no Git. A disponibilidade de clientes MCP remotos depende do produto/plano e das opções de conexão disponíveis na conta.

## Variáveis de ambiente

Configure apenas no gerenciador de segredos do host:

- `NEXORA_ADMIN_PASSWORD`: senha administrativa exclusiva, mínimo de 16 caracteres.
- `OPENROUTER_API_KEY`: opcional; ativa o roteador gratuito OpenRouter.
- `GROQ_API_KEY`: opcional; ativa Groq.
- `UPSTASH_REDIS_REST_URL` e `UPSTASH_REDIS_REST_TOKEN`: devem ser fornecidos juntos; obrigatórios em produção.
- `NODE_ENV=production`: ativa cookie Secure e exigência de Redis.
- `HOST=0.0.0.0` e `PORT`: conforme o host.

O arquivo `.env.example` contém apenas valores fictícios/vazios. Nunca publique credenciais.

## Custos e autorização

A Central não faz chamadas automáticas ao iniciar, ao carregar o painel ou ao consultar o status. Uma chamada a um provedor só ocorre quando uma mensagem é enviada e o provedor escolhido possui chave configurada.

Somente `openrouter/free` fixa a rota para modelos gratuitos. Groq pode ter cotas, limites e preços que variam por conta e podem mudar. Antes de cadastrar essas chaves, confirme o plano, faturamento e limites diretamente nos painéis dos provedores. A presença de uma chave indica apenas que ela foi configurada; não valida saldo, cota ou acesso ao modelo.

## Executar e testar

Requer Node.js 20 ou superior:

```sh
npm test
npm start
```

Em desenvolvimento local, o servidor escuta em `127.0.0.1:8787`.

## Limitações

- Sem Redis, sessões e rate limits ficam em memória e se perdem ao reiniciar; produção recusa iniciar sem Redis persistente.
- A senha é uma credencial administrativa única; não há contas individuais, recuperação de senha nem MFA.
- A Central ainda não executa tarefas no repositório nem modifica automaticamente o aplicativo Android.
- O Render Free é adequado apenas para pré-visualização, sujeito a suspensão por inatividade e filesystem efêmero.
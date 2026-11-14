# NEXORA AI Control Center — API inicial

Este serviço é um primeiro backend de integração, ainda não publicado e ainda não ligado à interface web.

## O que já está preparado

- API Node.js sem dependências de terceiros.
- Autenticação por token Bearer próprio do painel, comparado em tempo constante.
- Chave OpenRouter lida exclusivamente de variável de ambiente do servidor.
- Rota de chat fixa em `openrouter/free`; o navegador não pode escolher um modelo pago.
- Limite de 1.024 tokens de saída, 20 mensagens por solicitação, 12.000 caracteres por mensagem e 10 solicitações por minuto por endereço.
- Não registra prompts, respostas, tokens de autenticação ou chaves nos logs.
- Respostas sem cache e sem detalhes internos do provedor.
- Testes automatizados com chamadas externas simuladas.

## Variáveis necessárias

Configure no gerenciador de segredos do serviço de hospedagem:

- `NEXORA_CONTROL_TOKEN`: segredo aleatório exclusivo para autenticar o painel (mínimo de 32 caracteres).
- `OPENROUTER_API_KEY`: chave da API OpenRouter.
- `PORT`: porta fornecida pelo host, se aplicável.

O arquivo `.env.example` contém apenas nomes e valores fictícios. Nunca publique valores reais.

## Executar e testar localmente

Requer Node.js 20 ou superior:

```sh
npm test
npm start
```

O servidor escuta em `127.0.0.1` por padrão, para não ficar exposto acidentalmente em uma rede local.

## Limitações intencionais desta etapa

- Ainda não existe login visual, gerenciamento de usuários ou integração com o HTML.
- Ainda não há hospedagem, domínio ou publicação.
- Somente o roteador gratuito do OpenRouter está permitido; provedores pagos ficam bloqueados.
- O roteador gratuito pode ter limites de uso e disponibilidade definidos pelo OpenRouter. Não há garantia de resposta em toda chamada.
- O limite por endereço é local ao processo; uma implantação com múltiplas instâncias precisará de rate limiter compartilhado.
- Antes de publicar, configurar HTTPS, proteção de origem/reverse proxy, gestão de sessão adequada, limites globais e monitoramento operacional.

Documentação da API usada: https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request

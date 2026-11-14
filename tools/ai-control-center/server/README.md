# NEXORA AI Control Center — login e API inicial

O backend agora serve a interface web e a API na mesma origem. A Central ainda não está publicada nem pronta para uso público.

## O que esta etapa implementa

- Servidor Node.js sem dependências externas.
- Tela de login integrada à interface.
- Senha administrativa lida somente da variável `NEXORA_ADMIN_PASSWORD`; mínimo de 16 caracteres.
- Sessão aleatória em cookie `HttpOnly`, `SameSite=Strict`, com validade de 12 horas; o navegador nunca recebe a chave do provedor.
- Cookie `Secure` ativado quando `NODE_ENV=production`; produção exige HTTPS no host.
- Verificação de origem para login e logout, limite de tentativas de login e encerramento de sessão.
- Interface HTML/CSS/JS servida pelo mesmo processo e mesma origem da API.
- Chave OpenRouter lida exclusivamente de variável de ambiente do servidor.
- Rota de chat fixa em `openrouter/free`; o cliente não escolhe modelo e modelos pagos seguem bloqueados.
- Limites de 1.024 tokens de saída, 20 mensagens por solicitação, 12.000 caracteres por mensagem e 10 solicitações por minuto por endereço.
- Testes automatizados com chamadas externas simuladas.

## Variáveis necessárias

Configure no gerenciador de segredos do serviço de hospedagem:

- `NEXORA_ADMIN_PASSWORD`: senha administrativa exclusiva, com pelo menos 16 caracteres.
- `OPENROUTER_API_KEY`: chave da API OpenRouter, quando a integração for ativada.
- `NODE_ENV=production`: ativa o atributo Secure do cookie em produção.
- `HOST=0.0.0.0`: necessário em hosts que exigem escuta na interface de rede.
- `PORT`: porta fornecida pelo host.

O arquivo `.env.example` contém somente valores fictícios. Nunca publique valores reais.

## Executar e testar

Requer Node.js 20 ou superior:

```sh
npm test
npm start
```

Em desenvolvimento local, o servidor escuta em `127.0.0.1:8787`. Acesse `http://127.0.0.1:8787`.

## Limitações antes de publicar

- Sessões ficam em memória e são apagadas quando o processo reinicia. Esta etapa é adequada a uma única instância; produção contínua precisará de armazenamento de sessão compartilhado e expiração.
- O limite de solicitações também é local ao processo e precisará ser compartilhado em implantação com múltiplas instâncias.
- A senha é uma credencial administrativa única, não há contas individuais, recuperação de senha nem MFA.
- Antes de expor à internet, configurar HTTPS, hospedagem, armazenamento de sessão adequado, limites compartilhados, monitoramento e política de backup.
- O painel ainda não envia tarefas para o repositório nem modifica automaticamente o emulador.
- Nenhuma chave foi cadastrada e nenhum provedor foi chamado nos testes.

O roteador gratuito pode ter limites e disponibilidade definidos pelo provedor; não há garantia de resposta em toda chamada.

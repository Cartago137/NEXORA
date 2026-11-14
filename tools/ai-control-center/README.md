# NEXORA — Central de Controle de IA

Painel web privado e independente do aplicativo Android, pensado para uso em celular e computador.

## Estado atual

- Interface responsiva em português com login administrativo.
- Sessão no servidor com cookie HttpOnly e SameSite=Strict.
- Visão geral dos provedores e estado da integração OpenRouter.
- Primeira interface de conversa conectada à rota protegida `POST /api/chat`.
- Roteamento fixo para `openrouter/free`; não existe seletor de modelos pagos.
- Limites de tamanho/histórico e frequência de chamadas.
- Testes automatizados para autenticação, sessão, chat, validação e proteção contra requisições de outra origem.
- Integrações Groq e Gemini são apenas planejadas; não estão conectadas.
- Ainda não está hospedado nem publicado. Nenhuma chave real ou senha de produção foi configurada.

## Requisitos

- Node.js 20 ou superior.
- Uma senha administrativa exclusiva com pelo menos 16 caracteres.
- Uma chave OpenRouter configurada somente no ambiente do servidor para testar chamadas reais.

## Executar em ambiente local

1. Copie `.env.example` para `.env`.
2. Defina `NEXORA_ADMIN_PASSWORD` com uma senha forte e exclusiva.
3. Defina `OPENROUTER_API_KEY` apenas se desejar habilitar chamadas ao provedor.
4. Instale/execute sem dependências externas:

```sh
cd tools/ai-control-center/server
npm test
npm start
```

O servidor usa `127.0.0.1:8787` por padrão. Para um ambiente de hospedagem, o host pode definir `HOST=0.0.0.0`, `PORT` e `NODE_ENV=production`. A publicação exige HTTPS e configuração segura do ambiente.

## Segurança e limites conhecidos

- Chaves de API ficam somente em variáveis de ambiente do servidor, nunca no HTML/JavaScript do navegador nem no Git.
- O navegador não recebe a chave do provedor.
- A sessão usa um identificador aleatório no cookie; o servidor guarda apenas o hash da sessão.
- Em produção, o cookie recebe o atributo Secure; HTTPS deve ser fornecido pelo host.
- Requisições de escrita à API verificam a origem, e o cookie usa SameSite=Strict.
- A rota de chat aceita somente mensagens user/assistant, até 20 mensagens e 12.000 caracteres por mensagem.
- Respostas limitadas a 1.024 tokens; o servidor usa sempre `openrouter/free`.
- Limites atuais de login e chat, e sessões, ficam em memória. Eles são reiniciados ao reiniciar o processo e não são compartilhados entre várias instâncias.
- Por isso, esta versão é de desenvolvimento/validação, não está pronta para exposição pública ou múltiplas instâncias. Antes da publicação, será necessário adicionar armazenamento persistente/compartilhado para sessões e limites, revisar proteção operacional e configurar HTTPS.
- Não há MFA, recuperação de senha, contas individuais nem trilha de auditoria durável.
- Nenhuma IA pode fazer merge, publicar releases ou alterar automaticamente a branch principal. Mudanças futuras devem passar por branch e revisão humana.
- Os testes simulam o provedor; não fazem chamadas reais nem geram cobranças.

## Próximas etapas

1. Validar o fluxo do chat e os erros de provedor em ambiente de teste.
2. Preparar sessões e limites com armazenamento persistente/compartilhado.
3. Escolher a hospedagem e configurar HTTPS, senha e chave por meio dos segredos do host.
4. Publicar primeiro em ambiente privado de teste e validar no celular.
5. Só depois avaliar integrações adicionais e automação de tarefas.

**Importante:** não envie senhas ou chaves de API nesta conversa e não configure credenciais de produção antes de escolhermos a hospedagem.

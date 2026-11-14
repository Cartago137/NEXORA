// A interface ainda é demonstrativa. Nenhuma chave ou dado secreto é armazenado no navegador.
document.querySelectorAll('.nav-link').forEach((link) => {
  link.addEventListener('click', () => {
    document.querySelectorAll('.nav-link').forEach((item) => item.classList.remove('active'));
    link.classList.add('active');
  });
});

// O contador só será alimentado pelo backend quando a integração estiver implementada.
const providerCount = document.getElementById('provider-count');
if (providerCount) providerCount.setAttribute('aria-label', 'Nenhum provedor conectado');
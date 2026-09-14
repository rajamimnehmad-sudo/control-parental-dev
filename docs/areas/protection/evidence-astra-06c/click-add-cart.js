(async () => {
  const button = [...document.querySelectorAll('button')]
    .find(element => /agregar(?: al carrito)?/i.test(element.textContent || ''));
  if (!button) return {found: false};
  button.click();
  await new Promise(resolve => setTimeout(resolve, 2500));
  return {
    found: true,
    url: location.href,
    title: document.title,
    visibleText: document.body?.innerText?.slice(0, 1200) || '',
  };
})()

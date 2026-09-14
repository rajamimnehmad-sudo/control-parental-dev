(() => ({
  forms: [...document.forms].map((form, index) => ({
    index,
    actionOrigin: (() => { try { return new URL(form.action, location.href).origin; } catch { return null; } })(),
    controls: form.elements.length,
    visibleControls: [...form.elements].filter(element => {
      const rect = element.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0;
    }).length,
  })).slice(0, 20),
  productLinks: [...document.links]
    .filter(link => /\/p\/|\/producto\/|\/product\//i.test(link.pathname) || /MLA-\d+/i.test(link.href))
    .map(link => ({href: link.href, text: link.textContent?.trim().slice(0, 100) || ''}))
    .filter((item, index, all) => all.findIndex(other => other.href === item.href) === index)
    .slice(0, 12),
  cartLinks: [...document.links]
    .filter(link => /carrito|cart|bag|bolsa/i.test(`${link.href} ${link.textContent || ''}`))
    .map(link => ({href: link.href, text: link.textContent?.trim().slice(0, 100) || ''}))
    .slice(0, 12),
}))()

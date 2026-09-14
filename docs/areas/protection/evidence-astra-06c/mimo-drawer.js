(() => [...document.querySelectorAll('[class*="drawer" i],button')]
  .map((element, index) => {
    const rect = element.getBoundingClientRect();
    return {
      index,
      tag: element.tagName,
      className: typeof element.className === 'string' ? element.className.slice(0, 220) : '',
      rect: [rect.x, rect.y, rect.width, rect.height],
      svgCount: element.querySelectorAll('svg').length,
      html: element.outerHTML.slice(0, 400),
    };
  })
  .filter(item => item.className.toLowerCase().includes('drawer'))
  .slice(0, 40))()

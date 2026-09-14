(() => ({
  controls: [...document.querySelectorAll('button,[role="button"],a')]
    .map((element, index) => {
      const rect = element.getBoundingClientRect();
      return {
        index,
        tag: element.tagName,
        label: element.getAttribute('aria-label') || element.textContent?.trim().slice(0, 80) || '',
        title: element.getAttribute('title') || '',
        className: typeof element.className === 'string' ? element.className.slice(0, 120) : '',
        html: element.outerHTML.slice(0, 300),
        visible: rect.width > 0 && rect.height > 0 && getComputedStyle(element).visibility !== 'hidden',
        rect: [rect.x, rect.y, rect.width, rect.height],
        svgCount: element.querySelectorAll('svg').length,
        imageCount: element.querySelectorAll('img').length,
      };
    })
    .filter(item => item.visible && item.rect[1] < innerHeight)
    .slice(0, 40),
}))()

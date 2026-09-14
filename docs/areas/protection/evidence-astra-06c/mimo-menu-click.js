(async () => {
  const trigger = document.querySelector('.vtex-store-drawer-0-x-openIconContainer--headerMobile');
  if (!trigger) return { trigger: false };
  const icon = trigger.querySelector('svg');
  const triggerRect = trigger.getBoundingClientRect();
  const iconRect = icon?.getBoundingClientRect();
  trigger.click();
  await new Promise(resolve => setTimeout(resolve, 800));
  const drawer = document.querySelector('.vtex-store-drawer-0-x-drawer--headerMobile');
  const drawerRect = drawer?.getBoundingClientRect();
  return {
    trigger: true,
    triggerRect: [triggerRect.x, triggerRect.y, triggerRect.width, triggerRect.height],
    iconRect: iconRect && [iconRect.x, iconRect.y, iconRect.width, iconRect.height],
    iconSafe: icon?.getAttribute('data-glosh-icon-safe') || null,
    drawerClass: drawer?.className || null,
    drawerRect: drawerRect && [drawerRect.x, drawerRect.y, drawerRect.width, drawerRect.height],
    drawerText: drawer?.textContent?.trim().slice(0, 400) || '',
  };
})()

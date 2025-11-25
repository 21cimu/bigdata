// User center page logic: read-only by default, editable after clicking "修改"
(function(){
  const ctx = window.location.pathname.replace(/\/[^/]*$/, '') || '';
  const base = window.location.origin + ctx;
  const state = { editing: false, user: null };

  function el(id){ return document.getElementById(id); }

  async function init(){
    bindEvents();
    await loadUser();
  }

  function bindEvents(){
    const avatarFile = el('avatarFile'); if (avatarFile) avatarFile.addEventListener('change', onAvatarChange);
    const removeBtn = el('removeAvatarBtn'); if (removeBtn) removeBtn.addEventListener('click', removeAvatar);
    const saveBtn = el('saveBtn'); if (saveBtn) saveBtn.addEventListener('click', onSave);
    const cancelBtn = el('cancelBtn'); if (cancelBtn) cancelBtn.addEventListener('click', onCancel);
    const logoutBtn = el('logoutBtn'); if (logoutBtn) logoutBtn.addEventListener('click', onLogout);
    const changePwdBtn = el('changePwdBtn'); if (changePwdBtn) changePwdBtn.addEventListener('click', openPwdModal);
    const pwdCancel = el('pwdCancel'); if (pwdCancel) pwdCancel.addEventListener('click', closePwdModal);
    const pwdSave = el('pwdSave'); if (pwdSave) pwdSave.addEventListener('click', onChangePassword);
    const editBtn = el('editBtn'); if (editBtn) editBtn.addEventListener('click', () => setEditMode(true));
    const backHomeBtn = el('backHomeBtn'); if (backHomeBtn) backHomeBtn.addEventListener('click', () => { window.location.href = base + '/index.html'; });
  }

  async function loadUser(){
    try{
      const resp = await fetch(base + '/api/user');
      const data = await resp.json();
      if (!data || !data.success) {
        window.location.href = base + '/login.html';
        return;
      }
      const user = data.user || {};
      state.user = user;
      if (el('avatarImg')) el('avatarImg').src = user.avatar || el('avatarImg').src;
      if (el('displayName')) el('displayName').textContent = user.username || '用户';
      if (el('userId')) el('userId').textContent = 'ID: ' + (user.id || '-');
      if (el('usernameInput')) el('usernameInput').value = user.username || '';
      if (el('emailInput')) el('emailInput').value = user.email || '';
      if (el('phoneInput')) el('phoneInput').value = user.phone || '';
      if (el('usernameInput')) el('usernameInput').setAttribute('data-user-id', user.id || '');
      setEditMode(false);
    }catch(e){
      console.debug('loadUser error', e);
      window.location.href = base + '/login.html';
    }
  }

  function onAvatarChange(e){
    if (!state.editing) return;
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    if (!f.type.startsWith('image/')) { try { window.notify && window.notify.error('请选择图片文件'); } catch (e) { alert('请选择图片文件'); } return; }
    const reader = new FileReader();
    reader.onload = function(ev){ el('avatarImg').src = ev.target.result; el('avatarImg').setAttribute('data-durl', ev.target.result); };
    reader.readAsDataURL(f);
  }

  function removeAvatar(){
    if (!state.editing) return;
    if (el('avatarImg')) {
      el('avatarImg').src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" fill="%231976D2"/><circle cx="32" cy="22" r="12" fill="%23ffffff"/><rect x="8" y="40" width="48" height="12" rx="6" fill="%23ffffff"/></svg>';
      el('avatarImg').removeAttribute('data-durl');
    }
    const f = el('avatarFile'); if (f) f.value = '';
  }

  async function onSave(){
    if (!state.editing) { setEditMode(true); return; }
    const username = (el('usernameInput').value || '').trim();
    const email = (el('emailInput').value || '').trim();
    const phone = (el('phoneInput').value || '').trim();
    if (!username) { try { window.notify && window.notify.error('用户名不能为空'); } catch (e) { alert('用户名不能为空'); } return; }

    const payload = { username, email, phone };
    const avatarEl = el('avatarImg');
    if (avatarEl) {
      if (avatarEl.getAttribute('data-durl')) payload.avatar = avatarEl.getAttribute('data-durl');
      else if (!avatarEl.src || avatarEl.src.indexOf('data:image') === -1) payload.avatar = avatarEl.src;
      else payload.avatar = null;
    }

    try{
      const resp = await fetch(base + '/api/user', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      const data = await resp.json();
      if (data && data.success) {
        try { window.notify && window.notify.success('保存成功'); } catch (e) {}
        window.location.href = base + '/index.html';
      } else {
        try { window.notify && window.notify.error('保存失败: ' + (data && data.message ? data.message : '未知错误')); } catch (e) { alert('保存失败: ' + (data && data.message ? data.message : '未知错误')); }
      }
    }catch(e){
      console.debug('save error', e);
      try { window.notify && window.notify.error('请求失败: ' + e.message); } catch (err) { alert('请求失败: ' + e.message); }
    }
  }

  function openPwdModal(){
    const modal = el('pwdModal');
    if (!modal) return;
    el('pwdCurrent').value = '';
    el('pwdNew').value = '';
    el('pwdConfirm').value = '';
    modal.style.display = 'flex';
  }

  function closePwdModal(){
    const modal = el('pwdModal');
    if (modal) modal.style.display = 'none';
  }

  async function onChangePassword(){
    const currentPwd = (el('pwdCurrent').value || '').trim();
    const newPwd = (el('pwdNew').value || '').trim();
    const confirmPwd = (el('pwdConfirm').value || '').trim();
    if (!currentPwd || !newPwd || !confirmPwd) { try { window.notify && window.notify.error('请完整填写密码信息'); } catch (e) { alert('请完整填写密码信息'); } return; }
    if (newPwd !== confirmPwd) { try { window.notify && window.notify.error('两次输入的新密码不一致'); } catch (e) { alert('两次输入的新密码不一致'); } return; }
    try{
      const payload = { password: newPwd, currentPassword: currentPwd };
      const resp = await fetch(base + '/api/user', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      let data;
      try { data = await resp.json(); } catch (e) { data = null; }
      if (!resp.ok && (!data || data.success === undefined)) {
        const txt = data && data.message ? data.message : (resp.status + ' ' + resp.statusText);
        try { window.notify && window.notify.error('修改失败: ' + txt); } catch (e) { alert('修改失败: ' + txt); }
        return;
      }
      if (data && data.success) {
        try { window.notify && window.notify.success('密码修改成功'); } catch (e) { alert('密码修改成功'); }
        closePwdModal();
        try { await fetch(base + '/api/auth?action=logout', { method: 'POST' }); } catch (e) {}
        try { localStorage.setItem('lastNotify', JSON.stringify({ type: 'success', message: '密码已更新，请重新登录' })); } catch (e) {}
        window.location.href = base + '/login.html';
      } else {
        const msg = data && data.message ? data.message : '未知错误';
        try { window.notify && window.notify.error('修改失败: ' + msg); } catch (e) { alert('修改失败: ' + msg); }
      }
    } catch (e) {
      try { window.notify && window.notify.error('请求失败: ' + e.message); } catch (err) { alert('请求失败: ' + e.message); }
    }
  }

  async function onLogout(){
    try{ await fetch(base + '/api/auth?action=logout', { method: 'POST' }); }catch(e){}
    try { localStorage.setItem('lastNotify', JSON.stringify({ type: 'info', message: '已登出' })); } catch (e) {}
    window.location.href = base + '/login.html';
  }

  function onCancel(){
    resetFormFromState();
    setEditMode(false);
  }

  function resetFormFromState(){
    const user = state.user || {};
    if (el('usernameInput')) el('usernameInput').value = user.username || '';
    if (el('emailInput')) el('emailInput').value = user.email || '';
    if (el('phoneInput')) el('phoneInput').value = user.phone || '';
    if (el('avatarImg')) {
      el('avatarImg').src = user.avatar || el('avatarImg').src;
      el('avatarImg').removeAttribute('data-durl');
    }
    const f = el('avatarFile'); if (f) f.value = '';
  }

  function setEditMode(flag){
    state.editing = !!flag;
    const inputs = [el('usernameInput'), el('emailInput'), el('phoneInput'), el('avatarFile'), el('removeAvatarBtn'), el('changePwdBtn')];
    inputs.forEach(i => { if (i) i.disabled = !state.editing; });
    const saveBtn = el('saveBtn'); if (saveBtn) { saveBtn.disabled = !state.editing; saveBtn.style.display = state.editing ? '' : 'none'; }
    const cancelBtn = el('cancelBtn'); if (cancelBtn) cancelBtn.style.display = state.editing ? '' : 'none';
    const editBtn = el('editBtn'); if (editBtn) editBtn.style.display = state.editing ? 'none' : '';
  }

  document.addEventListener('DOMContentLoaded', init);
})();

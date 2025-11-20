(function(){
   const ctx = window.location.pathname.replace(/\/[^/]*$/, '') || '';
   const base = window.location.origin + ctx;
  // Toggle admin debug panel (false = disabled)
  const ADMIN_DEBUG = false;

   function el(id){ return document.getElementById(id); }

   // 通用 fetch 辅助：记录请求并解析 JSON；在遇到 404 时抛出特定对象以便调用方展示友好信息
   async function fetchJson(url, opts) {
     console.debug('admin fetch:', url, opts || {});
     const resp = await fetch(url, opts);
     if (resp.status === 404) {
       // 抛出包含 code/url 以便上层处理
       throw { code: 404, url };
     }
     if (!resp.ok) {
       const txt = await resp.text();
       throw new Error('请求失败: ' + resp.status + ' ' + (txt ? txt.substring(0,200) : ''));
     }
     const ct = resp.headers.get('content-type') || '';
     const text = await resp.text();
     if (ct.includes('application/json')) {
       try { return JSON.parse(text); } catch (e) { throw new Error('JSON 解析失败: ' + e.message); }
     }
     // 尝试解析文本为 JSON（容错）
     try { return JSON.parse(text); } catch (e) { throw new Error('返回非 JSON 内容'); }
   }

   // 显示横幅，提示 admin API 未实现并提供切换模拟/重试按钮
    let adminApiMissingBannerShown = false;
    function showAdminBanner(missingUrl) {
      if (adminApiMissingBannerShown) return;
      adminApiMissingBannerShown = true;
      const header = document.querySelector('.admin-header');
      if (!header) return;
      const banner = document.createElement('div');
      banner.id = 'adminApiBanner';
      banner.style.background = '#fff3cd';
      banner.style.borderTop = '1px solid #ffeeba';
      banner.style.padding = '10px 16px';
      banner.style.color = '#856404';
      banner.style.display = 'flex';
      banner.style.alignItems = 'center';
      banner.style.justifyContent = 'space-between';
      banner.innerHTML = `<div>检测到 admin 后端接口未实现或返回 404（示例 URL: ${escapeHtml(missingUrl)}）。界面已自动切换到本地模拟数据以便演示。</div>`;
      const actions = document.createElement('div');
      const retryBtn = document.createElement('button'); retryBtn.className='btn'; retryBtn.textContent='重试接口';
      const toggleMockBtn = document.createElement('button'); toggleMockBtn.className='btn btn-primary'; toggleMockBtn.style.marginLeft='8px';
      // 文本根据当前 localStorage 状态显示
      const stored = localStorage.getItem('admin.useMock');
      const isMock = stored === null ? true : (stored === 'true');
      toggleMockBtn.textContent = isMock ? '关闭模拟数据' : '启用模拟数据';
      actions.appendChild(retryBtn); actions.appendChild(toggleMockBtn);
      banner.appendChild(actions);
      header.parentElement.insertBefore(banner, header.nextSibling);

      retryBtn.addEventListener('click', () => {
        // 简单地重新加载当前面板数据
        loadAnnouncements(); loadUsers(); loadInfoFiles(); loadInfoLogs();
      });
      toggleMockBtn.addEventListener('click', () => {
        // 切换所有模拟数据开关并持久化
        const current = localStorage.getItem('admin.useMock');
        const now = current === 'true' ? 'false' : 'true';
        localStorage.setItem('admin.useMock', now);
        const enable = now === 'true';
        useMockAnnouncements = enable; useMockUsers = enable; useMockFiles = enable; useMockLogs = enable;
        if (enable) { enableMockAnnouncements(); enableMockUsers(); enableMockFiles(); enableMockLogs(); }
        else { loadAnnouncements(); loadUsers(); loadInfoFiles(); loadInfoLogs(); }
        toggleMockBtn.textContent = enable ? '关闭模拟数据' : '启用模拟数据';
      });
    }

   async function init(){
     bindNav();
     bindButtons();
     await loadCurrentUser();
     // 根据 localStorage 决定是否启用本地模拟数据（默认启用）
     const stored = localStorage.getItem('admin.useMock');
     const useMockDefault = stored === null ? true : (stored === 'true');
     useMockAnnouncements = useMockDefault; useMockUsers = useMockDefault; useMockFiles = useMockDefault; useMockLogs = useMockDefault;
     if (useMockDefault) {
       enableMockAnnouncements(); enableMockUsers(); enableMockFiles(); enableMockLogs();
     }
     // 同时尝试请求真实后端（若存在将替换模拟数据）
     loadAnnouncements().catch(()=>{});
     loadUsers().catch(()=>{});
     // Load files by default to match default active sub-item
     loadInfoFiles().catch(()=>{});
   }

   // Updated bindNav: listens on .nav-header and respects new structure
   function bindNav(){
     // 绑定所有导航组头部点击事件
     document.querySelectorAll('.admin-sidebar .nav-group .nav-header').forEach(header => {
       header.addEventListener('click', () => {
         const group = header.closest('.nav-group');
         const panelName = group.getAttribute('data-panel');
         const hasSubNav = group.querySelector('.sub-nav');

         // 1. 处理样式交互
         if (hasSubNav) {
           // 如果有子菜单，点击 header 仅切换展开/折叠，不作为“选中”状态（高亮留给子项）
           group.classList.toggle('open');
         } else {
           // 如果没有子菜单（如用户管理），点击 header 即视为选中
           // 清除所有选中状态
           document.querySelectorAll('.nav-header, .sub-item').forEach(el => el.classList.remove('active'));
           // 高亮当前 header
           header.classList.add('active');
           // 关闭其他已展开的组（可选，根据需求决定是否手风琴效果，这里暂时保留独立展开）
           // document.querySelectorAll('.nav-group').forEach(g => { if(g!==group) g.classList.remove('open'); });
         }

         // 2. 处理面板切换逻辑
         // 仅当是顶级菜单且无子菜单，或者这是信息管理组(默认行为)时触发
         // 注意：对于信息管理，内容的切换主要由 sub-item 点击触发，这里主要处理初次展开

         if (panelName === 'info') {
           // 信息管理被点击（展开）时，确保默认显示文件面板，并高亮默认子项（如果当前没有高亮项）
           if (group.classList.contains('open')) {
             const activeSub = group.querySelector('.sub-item.active');
             if (!activeSub) {
                const def = group.querySelector('.sub-item[data-action="infoFiles"]');
                if(def) {
                    def.classList.add('active');
                    loadInfoFiles('/users'); // 加载默认视图
                }
             }
             // 确保面板可见
             document.querySelectorAll('.admin-main .panel').forEach(p => p.style.display = 'none');
             const t = el('panel-info'); if (t) t.style.display = '';
           }
         } else if (panelName === 'users') {
             // 用户管理
             document.querySelectorAll('.admin-main .panel').forEach(p => p.style.display = 'none');
             const t = el('panel-users'); if (t) t.style.display = '';
         } else if (panelName === 'board') {
             // 公告板展示视图
             document.querySelectorAll('.admin-main .panel').forEach(p => p.style.display = 'none');
             const t = el('panel-board'); if (t) t.style.display = '';
             loadBulletinBoard();
         }
       });
     });

     // 绑定子菜单项点击事件
     document.querySelectorAll('.admin-sidebar .sub-item').forEach(s => {
       // 排除动态生成的（稍后单独绑定或委托），这里绑定静态的
       if (s.parentElement.id === 'infoDirChildren') return;

       s.addEventListener('click', (ev) => {
         ev.stopPropagation();

         // 清除全局所有高亮
         document.querySelectorAll('.nav-header, .sub-item').forEach(x => x.classList.remove('active'));
         // 高亮自己
         s.classList.add('active');

         const action = s.getAttribute('data-action');
         const group = s.closest('.nav-group');
         if(group) group.classList.add('open'); // 确保父级展开

         // 切换面板
         document.querySelectorAll('.admin-main .panel').forEach(p => p.style.display = 'none');

         if (action === 'infoFiles') {
           const t = el('panel-info'); if (t) t.style.display = '';
           loadInfoFiles('/users');
         } else if (action === 'infoLogs') {
           const t = el('panel-info'); if (t) t.style.display = '';
           loadInfoLogs();
         } else if (action === 'infoAnn') {
           const t = el('panel-announce'); if (t) t.style.display = '';
           loadAnnouncements();
         }
       });
     });
   }

   // 更新动态目录生成函数，使其生成符合新 CSS 的结构
   function populateInfoDirContainer(items) {
    const ul = el('infoDirChildren'); if (!ul) return;
    ul.innerHTML = '';
    try {
      const dirs = getTopLevelDirsFromItems(items || []);
      if (!dirs || dirs.length === 0) return;

      dirs.forEach(d => {
        const li = document.createElement('li');
        // 创建子项
        const a = document.createElement('div'); // 使用 div 保持一致性
        a.className = 'sub-item';
        a.setAttribute('data-action','infoDir');
        a.setAttribute('data-path', d.path);
        // 添加文件夹图标前缀
        a.innerHTML = `<span style="opacity:0.7; margin-right:4px;">📁</span>${escapeHtml(d.name)}`;

        a.addEventListener('click', (ev) => {
          ev.preventDefault(); ev.stopPropagation();

          // 高亮处理
          document.querySelectorAll('.nav-header, .sub-item').forEach(x => x.classList.remove('active'));
          a.classList.add('active');

          // 确保面板显示
          document.querySelectorAll('.admin-main .panel').forEach(p => p.style.display = 'none');
          const target = el('panel-info'); if (target) target.style.display = '';
          loadInfoFiles(d.path);
        });

        li.appendChild(a);
        ul.appendChild(li);
      });

      // 匹配当前路径以保持高亮
      let matched = null;
      Array.from(ul.querySelectorAll('.sub-item')).forEach(a => {
           const p = a.getAttribute('data-path');
           if (p === currentFilePath) matched = a;
       });
      // 如果没有精确匹配，尝试匹配父路径
      if (!matched) {
        Array.from(ul.querySelectorAll('.sub-item')).forEach(a => {
             const p = a.getAttribute('data-path');
             if (p && (currentFilePath||'/').startsWith(p) && (!matched || p.length > matched.getAttribute('data-path').length)) matched = a;
         });
      }
      if (matched) {
        // 清除其他高亮
        document.querySelectorAll('.nav-header, .sub-item').forEach(x => x.classList.remove('active'));
        matched.classList.add('active');
      }
    } catch (e) { appendDebug('populateInfoDirContainer failed: ' + e); }
  }

   function bindButtons(){
     const newAnn = el('newAnnBtn'); if (newAnn) newAnn.addEventListener('click', openNewAnnModal);
     const annSearch = el('announceSearch'); if (annSearch) annSearch.addEventListener('input', () => { renderAnnouncementsFilter(el('announceSearch').value); });
     const newUser = el('newUserBtn'); if (newUser) newUser.addEventListener('click', openNewUserModal);
     const userSearch = el('userSearch'); if (userSearch) userSearch.addEventListener('input', () => renderUsersFilter(el('userSearch').value));
     const infoSearch = el('infoSearch'); if (infoSearch) infoSearch.addEventListener('input', () => {
       if (currentInfoView === 'files') renderFilesFilter(infoSearch.value);
       else if (currentInfoView === 'logs') loadInfoLogs();
       else if (currentInfoView === 'ann') renderAnnouncementsFilter(infoSearch.value);
     });
     const modalCancel = el('modalCancel'); if (modalCancel) modalCancel.addEventListener('click', () => { el('adminModal').style.display = 'none'; });

    // logout button: navigate to login and attempt server logout if endpoint exists
    const logoutBtn = el('logoutBtn'); if (logoutBtn) {
      logoutBtn.addEventListener('click', async () => {
        try {
          // try server logout endpoint if available
          await fetch(base + '/api/auth?action=logout', { method: 'POST' }).catch(()=>{});
        } catch(e) { /* ignore */ }
        // redirect to login page
        window.location.href = base + '/login.html';
      });
    }
  }

  // helper: human readable file size
  function humanSize(bytes){
    if (bytes==null || bytes===0) return '';
    const thresh = 1024;
    if (Math.abs(bytes) < thresh) return bytes + ' B';
    const units = ['KB','MB','GB','TB','PB','EB','ZB','YB'];
    let u = -1;
    do { bytes /= thresh; ++u; } while(Math.abs(bytes) >= thresh && u < units.length-1);
    return bytes.toFixed(bytes >= 10 || u<1 ? 0 : 1) + ' ' + units[u];
  }

  async function loadCurrentUser(){
    try{
      const resp = await fetch(base + '/api/auth?action=current');
      const data = await resp.json();
      if (!data || !data.loggedIn) { window.location.href = base + '/login.html'; return; }
      // Prefer display name, fall back to username; ensure 'root' shown as 'admin' role in user list handled elsewhere
      const name = data.user && (data.user.displayName || data.user.username || data.user.userName || data.user.name) || '管理员';
      el('adminUserName').textContent = name;
    }catch(e){ console.debug('loadCurrentUser failed', e); }
  }

   // --- simple on-page debug log to help diagnose click/load issues ---
   function ensureDebugPanel(){
     if (!ADMIN_DEBUG) return;
     if (document.getElementById('adminDebug')) return;
     try {
       const h = document.querySelector('.admin-header'); if (!h) return;
       const d = document.createElement('div'); d.id = 'adminDebug'; d.style.cssText = 'font-size:12px;color:#fff;background:#b00020;padding:6px 10px;position:fixed;right:12px;bottom:12px;z-index:9999;max-width:320px;opacity:0.95;'; d.textContent = 'admin debug:'; d.addEventListener('click', ()=>{ d.style.display='none'; });
       document.body.appendChild(d);
     } catch(e){ console.debug('ensureDebugPanel failed', e); }
   }
   function appendDebug(msg){
     if (!ADMIN_DEBUG) return;
     try{ ensureDebugPanel(); const d = document.getElementById('adminDebug'); if (!d) return; const ts = new Date().toLocaleTimeString(); const p = document.createElement('div'); p.textContent = ts + ' - ' + msg; p.style.whiteSpace = 'pre-wrap'; p.style.marginTop='4px'; d.appendChild(p); if (d.childNodes.length>30) d.removeChild(d.childNodes[1]); }catch(e){ console.debug('appendDebug failed', e); }
   }

  // visible error banner for runtime debugging (always shown) ------------------------------------------------
  function showFatalError(msg){
    try{
      // avoid creating multiple banners
      if (document.getElementById('adminFatalError')) return;
      const banner = document.createElement('div'); banner.id = 'adminFatalError';
      banner.style.cssText = 'position:fixed;left:12px;right:12px;top:64px;z-index:99999;background:#ffebe9;border:1px solid #f5c2c0;color:#a80000;padding:12px;border-radius:6px;box-shadow:0 6px 18px rgba(0,0,0,0.08);font-size:13px;';
      const pre = document.createElement('pre'); pre.style.whiteSpace='pre-wrap'; pre.style.margin='0'; pre.style.fontSize='13px'; pre.textContent = String(msg);
      const close = document.createElement('button'); close.className='btn'; close.textContent='关闭'; close.style.float='right'; close.style.marginLeft='8px'; close.addEventListener('click', ()=>{ banner.remove(); });
      banner.appendChild(close); banner.appendChild(pre);
      document.body.appendChild(banner);
      console.error('Admin page fatal error:', msg);
    }catch(e){ console.error('showFatalError failed', e); }
  }

  window.addEventListener('error', function(ev){ const m = ev && (ev.message || ev.error && ev.error.message) || String(ev); appendDebug('ERROR: ' + m); showFatalError(m); });
  window.addEventListener('unhandledrejection', function(ev){ const r = ev && ev.reason; const m = (r && (r.message || JSON.stringify(r))) || String(ev); appendDebug('UNHANDLEDREJ: ' + m); showFatalError(m); });

   // ---------------- Announcements ----------------
   let announcements = [];
   let useMockAnnouncements = false;
   function enableMockAnnouncements() {
     useMockAnnouncements = true;
     // sample data
     announcements = [
       { id: 1, title: '欢迎使用管理员控制台', author: '系统', content: '这是模拟公告，后端接口未实现时使用。', createdAt: Date.now() },
       { id: 2, title: '维护通知', author: '系统', content: '将在周末进行系统维护。', createdAt: Date.now() - 86400000 }
     ];
     renderAnnouncements(announcements);
   }
   // ---------------- Files (mock) ----------------
   let useMockFiles = false;
   let mockFiles = [];
   function enableMockFiles() {
     useMockFiles = true;
     mockFiles = [
       { path: '/user1/docs/readme.txt', owner: 'user1', size: 1024, mtime: Date.now() - 3600*1000 },
       { path: '/user2/photos/img1.jpg', owner: 'user2', size: 204800, mtime: Date.now() - 86400*1000 }
     ];
     renderFilesList(mockFiles);
   }
   // ---------------- Logs (mock) ----------------
   let useMockLogs = false;
   let mockLogs = [];
   function enableMockLogs() {
     useMockLogs = true;
     mockLogs = [
       { time: Date.now() - 60000, user: 'user1', action: '上传', detail: '/user1/docs/readme.txt' },
       { time: Date.now() - 3600000, user: 'user2', action: '删除', detail: '/user2/old.zip' }
     ];
     renderLogs(mockLogs);
   }

   async function loadAnnouncements(){
     try{
       const url = base + '/api/admin/announcements';
       const data = await fetchJson(url);
       announcements = data.items || [];
       renderAnnouncements(announcements);
     }catch(e){
       if (e && e.code === 404) {
         const out = el('annList'); if (out) {
           out.innerHTML = `<div class="muted">后端未实现接口 <strong>/api/admin/announcements</strong> (404)。<br>请求 URL: ${escapeHtml(e.url)} <br><button id="annRetry" class="btn">重试</button> <button id="annMock" class="btn">使用本地模拟数据</button></div>`;
           const btn = el('annRetry'); if (btn) btn.addEventListener('click', loadAnnouncements);
           const mock = el('annMock'); if (mock) mock.addEventListener('click', enableMockAnnouncements);
         }
         console.debug('admin announcements endpoint 404:', e.url);
         // 自动启用本地模拟数据，便于立即使用管理员页面（同时保留手动按钮）
         enableMockAnnouncements();
         const out2 = el('annList'); if (out2) {
           const note = document.createElement('div'); note.className = 'muted'; note.style.marginTop = '8px'; note.textContent = '已自动启用本地模拟公告数据以便演示（API 未实现）。'; out2.insertBefore(note, out2.firstChild);
         }
         // 展示顶部横幅，提示并允许切换或重试
         try { showAdminBanner(e.url); } catch(ex) { console.debug('showAdminBanner failed', ex); }
       } else { console.debug('loadAnnouncements failed', e); renderAnnouncements([]); }
     }
   }

   function renderAnnouncements(list){
     const out = el('annList'); if (!out) return;
     out.innerHTML = '';
     if (!list || list.length === 0) { out.innerHTML = '<div class="muted">暂无公告</div>'; return; }
     const wrapper = document.createElement('div');
     wrapper.className = 'data-table-wrapper';
     const table = document.createElement('table');
     table.className = 'data-table';
     table.innerHTML = `<thead><tr><th>标题</th><th>发布者</th><th>时间</th><th style="width:120px; text-align:center;">操作</th></tr></thead>`;
     const tb = document.createElement('tbody');
     list.forEach(a => {
       const tr = document.createElement('tr');
       tr.innerHTML = `<td>${escapeHtml(a.title)}</td><td class="text-muted">${escapeHtml(a.author||'')}</td><td class="text-muted">${new Date(a.createdAt||0).toLocaleString()}</td><td style="text-align:center;"><button class="btn btn-xs" data-id="${a.id}" data-action="edit">编辑</button> <button class="btn btn-xs btn-ghost-danger" data-id="${a.id}" data-action="del">删除</button></td>`;
       tb.appendChild(tr);
     });
     table.appendChild(tb);
     wrapper.appendChild(table);
     out.appendChild(wrapper);
     // wire actions
     out.querySelectorAll('button[data-action="edit"]').forEach(b => b.addEventListener('click', () => { const id = b.getAttribute('data-id'); openEditAnnModal(id); }));
     out.querySelectorAll('button[data-action="del"]').forEach(b => b.addEventListener('click', async () => { const id = b.getAttribute('data-id'); if (!confirm('确定删除此公告吗?')) return; await deleteAnn(id); }));
   }

   function renderAnnouncementsFilter(q){
     const f = q && q.trim().toLowerCase();
     if (!f) return renderAnnouncements(announcements);
     const filtered = announcements.filter(a => (a.title||'').toLowerCase().includes(f) || (a.content||'').toLowerCase().includes(f));
     renderAnnouncements(filtered);
   }

   function escapeHtml(s){ if (!s) return ''; return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

   async function openNewAnnModal(){
     const modal = el('adminModal'); if (!modal) return;
     modal.style.display = 'flex';
     el('adminModalBody').innerHTML = `<div style="display:flex;flex-direction:column;gap:8px;"><input id="annTitle" placeholder="标题" style="padding:8px;border:1px solid #ddd;border-radius:6px;"><textarea id="annContent" rows="6" style="padding:8px;border:1px solid #ddd;border-radius:6px;" placeholder="内容"></textarea></div>`;
     el('modalOk').onclick = async () => { const t = el('annTitle').value.trim(); const c = el('annContent').value.trim(); if (!t) { alert('请输入标题'); return; } await createAnn({ title: t, content: c }); modal.style.display='none'; };
   }

   async function openEditAnnModal(id){
     const ann = announcements.find(a=>String(a.id)===String(id));
     if (!ann) return alert('公告未找到');
     const modal = el('adminModal'); if (!modal) return;
     modal.style.display = 'flex';
     el('adminModalBody').innerHTML = `<div style="display:flex;flex-direction:column;gap:8px;"><input id="annTitle" value="${escapeHtml(ann.title)}" style="padding:8px;border:1px solid #ddd;border-radius:6px;"><textarea id="annContent" rows="6" style="padding:8px;border:1px solid #ddd;border-radius:6px;">${escapeHtml(ann.content)}</textarea></div>`;
     el('modalOk').onclick = async () => { const t = el('annTitle').value.trim(); const c = el('annContent').value.trim(); if (!t) { alert('请输入标题'); return; } await updateAnn(id, { title: t, content: c }); modal.style.display='none'; };
   }

   async function createAnn(payload){
     try{
       const url = base + '/api/admin/announcements';
       if (useMockAnnouncements) {
         // simulate create
         const id = (announcements.reduce((m,a)=>Math.max(m,a.id||0),0) || 0) + 1;
         announcements.unshift(Object.assign({ id, author: '管理员', createdAt: Date.now() }, payload));
         renderAnnouncements(announcements);
       } else {
         await fetchJson(url, { method: 'POST', headers: { 'Content-Type':'application/json' }, body: JSON.stringify(payload) });
         await loadAnnouncements();
       }
     }catch(e){ alert('创建失败: ' + (e.message || (e.code===404?('接口未实现: '+e.url):e))); }
   }
   async function updateAnn(id, payload){
     try{
       const url = base + '/api/admin/announcements/' + encodeURIComponent(id);
       if (useMockAnnouncements) {
         const idx = announcements.findIndex(a=>String(a.id)===String(id));
         if (idx !== -1) { announcements[idx] = Object.assign({}, announcements[idx], payload); renderAnnouncements(announcements); }
       } else {
         await fetchJson(url, { method: 'PUT', headers: { 'Content-Type':'application/json' }, body: JSON.stringify(payload) });
         await loadAnnouncements();
       }
     }catch(e){ alert('更新失败: ' + (e.message || (e.code===404?('接口未实现: '+e.url):e))); }
   }
   async function deleteAnn(id){
     try{
       const url = base + '/api/admin/announcements/' + encodeURIComponent(id);
       if (useMockAnnouncements) {
         announcements = announcements.filter(a=>String(a.id)!==String(id)); renderAnnouncements(announcements);
       } else {
         await fetchJson(url, { method: 'DELETE' });
         await loadAnnouncements();
       }
     }catch(e){ alert('删除失败: ' + (e.message || (e.code===404?('接口未实现: '+e.url):e))); }
   }

   // ---------------- Info management ----------------
   let currentInfoView = null; // 'files' | 'logs' | 'ann'
   let filesCache = [];
   let currentFilePath = '/users';
   // avoid duplicate rapid loads for same path
   let __lastLoadPath = null;
   let __lastLoadTs = 0;

   // load files under a given directory path (defaults to /users)
   async function loadInfoFiles(path){
     currentInfoView = 'files';
     const now = Date.now();
     const targetPath = (path && path.trim() !== '') ? path : currentFilePath || '/users';
     // dedupe: if requesting same path within 500ms, ignore
     if (__lastLoadPath === targetPath && (now - __lastLoadTs) < 500) {
       appendDebug('loadInfoFiles deduped for path=' + targetPath);
       return;
     }
     __lastLoadPath = targetPath; __lastLoadTs = now;
     appendDebug('loadInfoFiles called, path=' + (path||currentFilePath));
     if (path && path.trim() !== '') currentFilePath = path;
     if (!currentFilePath) currentFilePath = '/users';
     const content = el('info-content'); if (!content) return;
     content.innerHTML = '<div class="muted">正在加载用户文件...</div>';
     try{
       const url = base + '/api/admin/files' + (currentFilePath ? ('?path=' + encodeURIComponent(currentFilePath)) : '');
       const data = await fetchJson(url);
       appendDebug('loadInfoFiles fetch success, items=' + (data && data.items?data.items.length:0));
       filesCache = data.items || [];
       renderInfoBreadcrumb(currentFilePath);
       renderFilesList(filesCache);
     }catch(e){
       appendDebug('loadInfoFiles failed: ' + (e && (e.message || e.code || e.url) || e));
       if (e && e.code === 404) {
         content.innerHTML = `<div class="muted">后端未实现接口 <strong>/api/admin/files</strong> (404)。<br>请求 URL: ${escapeHtml(e.url)} <br><button id="filesRetry" class="btn">重试</button></div>`;
         const btn = el('filesRetry'); if (btn) btn.addEventListener('click', ()=>loadInfoFiles(currentFilePath));
         console.debug('admin files endpoint 404:', e.url);
         try { showAdminBanner(e.url); } catch (ex) { console.debug('showAdminBanner failed', ex); }
         // fallback to mock
         enableMockFiles();
       } else {
         content.innerHTML = '<div class="muted">加载失败</div>';
       }
      }
   }

   function renderInfoBreadcrumb(path){
     appendDebug('renderInfoBreadcrumb path=' + path);
     const bc = el('info-breadcrumb'); if (!bc) return; bc.innerHTML = '';
     const parts = path.split('/').filter(p=>p!=='');
     let acc = '';
     const root = document.createElement('a'); root.href = '#'; root.textContent = '/'; root.style.marginRight='8px'; root.addEventListener('click', (ev)=>{ ev.preventDefault(); loadInfoFiles('/'); });
     bc.appendChild(root);
     parts.forEach((p, idx)=>{
       acc += '/' + p;
       const a = document.createElement('a'); a.href = '#'; a.textContent = p; a.style.marginRight='8px';
       a.addEventListener('click', (ev) => {
         ev.preventDefault();
         ev.stopPropagation();
         // rebuild path from breadcrumb anchors up to this one
         const anchors = Array.from(bc.querySelectorAll('a'));
         const idxThis = anchors.indexOf(a);
         if (idxThis <= 0) { loadInfoFiles('/'); return; }
         const parts = [];
         anchors.forEach((aa, i) => { if (i > 0 && i <= idxThis) parts.push(aa.textContent); });
         const newPath = '/' + parts.join('/');
         loadInfoFiles(newPath);
       });
       bc.appendChild(a);
       if (idx < parts.length-1) {
         const sep = document.createElement('span'); sep.textContent = '/'; sep.style.marginRight='8px'; bc.appendChild(sep);
       }
     });
   }

   // Build a nested directory tree from a flat items list.
   function buildDirTreeFromItems(items, basePath) {
     const base = (basePath || currentFilePath || '/');
     const normBase = base === '/' ? '/' : (base.endsWith('/') ? base : base + '/');
     // root node representing the base path
     const root = { name: normBase === '/' ? '/' : (normBase.replace(/\/$/, '')), path: normBase === '/' ? '/' : normBase.replace(/\/$/, ''), children: new Map(), parent: null };
     (items || []).forEach(it => {
       const p = it.path || '';
       // determine relative path parts with respect to normBase when possible
       let rel = p;
       if (p.startsWith(normBase)) rel = p.substring(normBase.length);
       else if (p.startsWith('/')) rel = p.substring(1);
       const parts = rel.split('/').filter(Boolean);
       if (parts.length === 0) return;
       let node = root;
       parts.forEach(part => {
         // compute absolute child path
         const childPath = (node.path === '/' ? '/' + part : node.path + '/' + part);
         if (!node.children.has(part)) {
           const child = { name: part, path: childPath, children: new Map(), parent: node };
           node.children.set(part, child);
         }
         node = node.children.get(part);
       });
     });
     return root;
   }

  // derive top-level directories (same logic as earlier implementation in renderFilesList)
  function getTopLevelDirsFromItems(items, basePath) {
    const base = (basePath || currentFilePath || '/');
    const normBase = base === '/' ? '/' : (base.endsWith('/') ? base : base + '/');
    const dirSet = new Set();
    (items || []).forEach(it => {
      const p = it.path || '';
      if (!p.startsWith(normBase)) {
        const parts = p.split('/').filter(Boolean);
        if (parts.length >= 1) dirSet.add('/' + parts[0]);
      } else {
        const rel = p.substring(normBase.length);
        const parts = rel.split('/').filter(Boolean);
        if (parts.length <= 1) return; // no subdir
        dirSet.add(normBase + parts[0]);
      }
    });
    return Array.from(dirSet).map(d => {
      const parts = d.split('/').filter(Boolean);
      return { path: d, name: parts.length ? parts[parts.length-1] : d };
    });
  }

   // populate the #infoDirContainer in the sidebar with directory entries
  function populateInfoDirContainer(items) {
    const ul = el('infoDirChildren'); if (!ul) return;
    ul.innerHTML = '';
    try {
      const dirs = getTopLevelDirsFromItems(items || []);
      if (!dirs || dirs.length === 0) return;

      dirs.forEach(d => {
        const li = document.createElement('li');
        // 创建子项
        const a = document.createElement('div'); // 使用 div 保持一致性
        a.className = 'sub-item';
        a.setAttribute('data-action','infoDir');
        a.setAttribute('data-path', d.path);
        // 添加文件夹图标前缀
        a.innerHTML = `<span style="opacity:0.7; margin-right:4px;">📁</span>${escapeHtml(d.name)}`;

        a.addEventListener('click', (ev) => {
          ev.preventDefault(); ev.stopPropagation();

          // 高亮处理
          document.querySelectorAll('.nav-header, .sub-item').forEach(x => x.classList.remove('active'));
          a.classList.add('active');

          // 确保面板显示
          document.querySelectorAll('.admin-main .panel').forEach(p => p.style.display = 'none');
          const target = el('panel-info'); if (target) target.style.display = '';
          loadInfoFiles(d.path);
        });

        li.appendChild(a);
        ul.appendChild(li);
      });

      // 匹配当前路径以保持高亮
      let matched = null;
      Array.from(ul.querySelectorAll('.sub-item')).forEach(a => {
           const p = a.getAttribute('data-path');
           if (p === currentFilePath) matched = a;
       });
      // 如果没有精确匹配，尝试匹配父路径
      if (!matched) {
        Array.from(ul.querySelectorAll('.sub-item')).forEach(a => {
             const p = a.getAttribute('data-path');
             if (p && (currentFilePath||'/').startsWith(p) && (!matched || p.length > matched.getAttribute('data-path').length)) matched = a;
         });
      }
      if (matched) {
        // 清除其他高亮
        document.querySelectorAll('.nav-header, .sub-item').forEach(x => x.classList.remove('active'));
        matched.classList.add('active');
      }
    } catch (e) { appendDebug('populateInfoDirContainer failed: ' + e); }
  }

   // --- 新增辅助函数：根据文件扩展名获取图标/颜色 ---
   function getFileIcon(filename, isDir) {
     if (isDir) return { icon: '📁', color: '#FFC107' }; // 文件夹
     const ext = filename.split('.').pop().toLowerCase();
     switch(ext) {
       case 'jpg': case 'jpeg': case 'png': case 'gif': case 'bmp': case 'svg':
         return { icon: '🖼️', color: '#FB8C00' }; // 图片
       case 'mp4': case 'mkv': case 'webm': case 'avi': case 'mov':
         return { icon: '🎬', color: '#E53935' }; // 视频
       case 'mp3': case 'wav': case 'flac': case 'ogg':
         return { icon: '🎵', color: '#8E24AA' }; // 音频
       case 'zip': case 'rar': case '7z': case 'tar': case 'gz':
         return { icon: '📦', color: '#795548' }; // 压缩包
       case 'pdf':
         return { icon: '📄', color: '#D32F2F' }; // PDF
       case 'doc': case 'docx': case 'txt': case 'md':
         return { icon: '📝', color: '#1976D2' }; // 文档
       case 'xls': case 'xlsx': case 'csv':
         return { icon: '📊', color: '#43A047' }; // 表格
       case 'ppt': case 'pptx':
         return { icon: '📉', color: '#F4511E' }; // 幻灯片
       case 'exe': case 'msi': case 'apk': case 'app':
         return { icon: '💿', color: '#607D8B' }; // 程序
       case 'js': case 'html': case 'css': case 'json': case 'xml': case 'java': case 'py': case 'c': case 'cpp':
         return { icon: '💻', color: '#0288D1' }; // 代码
       default:
         return { icon: '📄', color: '#9E9E9E' }; // 未知
     }
   }

    // --- 新增辅助函数：根据日志动作获取徽章样式 ---
   function getActionBadgeClass(action) {
     if (!action) return 'badge-gray';
     const act = action.toLowerCase();
     if (act.includes('删') || act.includes('del') || act.includes('remove')) return 'badge-red';
     if (act.includes('传') || act.includes('up') || act.includes('add') || act.includes('crea')) return 'badge-green';
     if (act.includes('改') || act.includes('edit') || act.includes('upd')) return 'badge-blue';
     if (act.includes('登') || act.includes('log')) return 'badge-orange';
     return 'badge-gray';
   }

    // --- 替换 renderFilesList 函数 ---
   function renderFilesList(items){
     appendDebug('renderFilesList items=' + (items?items.length:0));
     const c = el('info-content'); if (!c) return; c.innerHTML = '';
     if (!items || items.length === 0) {
        c.innerHTML = '<div class="muted" style="text-align:center; padding:40px;">暂无文件</div>';
        return;
      }
     // 创建外层容器
     const wrapper = document.createElement('div');
     wrapper.className = 'data-table-wrapper';
      const table = document.createElement('table');
      table.className = 'data-table'; // 使用新样式类
     table.innerHTML = `
       <thead>
         <tr>
           <th>文件名/路径</th>
           <th style="width:120px">所有者</th>
           <th style="width:100px; text-align:right;">大小</th>
           <th style="width:180px">修改时间</th>
           <th style="width:100px; text-align:center;">操作</th>
         </tr>
       </thead>`;
     const tb = document.createElement('tbody');
     // 1. 数据预处理：分离文件夹和文件
     let dirs = items.filter(i=>i.isDirectory);
     let filesOnly = items.filter(i=>i.isDirectory===false);
     // 容错：如果没有 isDirectory 字段，根据路径判断
     const providedFlags = items.some(i=>i.hasOwnProperty('isDirectory'));
     if (!providedFlags) {
       const dirSet = new Set();
       const fileList = [];
       const base = (currentFilePath || '/');
       const normBase = base === '/' ? '/' : (base.endsWith('/') ? base : base + '/');
       items.forEach(it => {
         const p = it.path || '';
         if (!p.startsWith(normBase)) {
           const parts = p.split('/').filter(Boolean);
           if (parts.length >= 1) dirSet.add('/' + parts[0]);
         } else {
           const rel = p.substring(normBase.length);
           const parts = rel.split('/').filter(Boolean);
           if (parts.length === 0) return;
           if (parts.length === 1) fileList.push(it);
           else dirSet.add(normBase + parts[0]);
         }
       });
       dirs = Array.from(dirSet).map(d => ({ path: d, owner: '-', isDirectory: true, mtime: 0 }));
       filesOnly = fileList;
     }
      // 2. 渲染文件夹行
     dirs.forEach(it=>{
       const tr = document.createElement('tr');
       const name = (it.path||'').split('/').filter(Boolean).pop();
       tr.innerHTML = `
         <td>
           <a href="#" class="dir-link" data-path="${escapeHtml(it.path||'')}" title="${escapeHtml(it.path)}">
             <span class="file-icon">📁</span>
             <span class="file-name">${escapeHtml(name||it.path||'')}</span>
           </a>
         </td>
         <td><span class="badge badge-gray">目录</span></td>
         <td style="text-align:right" class="text-muted">-</td>
         <td class="text-muted">${it.mtime ? new Date(it.mtime).toLocaleString() : '-'}</td>
         <td></td>
       `;
       tb.appendChild(tr);
     });
      // 3. 渲染文件行
     filesOnly.forEach(it=>{
       const tr = document.createElement('tr');
       const name = it.path.split('/').filter(Boolean).pop();
       const fileMeta = getFileIcon(name, false); // 获取图标
       const ownerBadge = it.owner ? `<span class="badge badge-blue">${escapeHtml(it.owner)}</span>` : '<span class="badge badge-gray">System</span>';
       tr.innerHTML = `
         <td>
           <div class="file-cell" title="${escapeHtml(it.path)}">
             <span class="file-icon">${fileMeta.icon}</span>
             <span class="file-name">${escapeHtml(name||it.path||'')}</span>
           </div>
         </td>
         <td>${ownerBadge}</td>
         <td style="text-align:right" class="text-mono">${escapeHtml(humanSize(it.size))}</td>
         <td class="text-muted">${new Date(it.mtime||0).toLocaleString()}</td>
         <td style="text-align:center;">
           <button class="btn btn-xs btn-ghost-danger" data-path="${escapeHtml(it.path||'')}" data-action="delFile" title="删除文件">删除</button>
         </td>
       `;
       tb.appendChild(tr);
     });
      table.appendChild(tb);
     wrapper.appendChild(table);
     c.appendChild(wrapper);
      // 绑定事件
     c.querySelectorAll('button[data-action="delFile"]').forEach(b=>b.addEventListener('click', async ()=>{
        if (!confirm('确定删除该文件? 此操作无法撤销。')) return;
        const p = b.getAttribute('data-path');
        await adminDeleteFile(p);
      }));
     c.querySelectorAll('a.dir-link').forEach(a=>a.addEventListener('click', (ev)=>{
        ev.preventDefault(); ev.stopPropagation();
        const p = a.getAttribute('data-path');
        if (p) loadInfoFiles(p);
      }));
     try {
      populateInfoDirContainer(items || []);
     } catch(e) { appendDebug('populateInfoDirContainer failed: ' + e); }
   }

   function renderFilesFilter(q){
     const f = q && q.trim().toLowerCase();
     if (!f) return renderFilesList(filesCache);
     const filtered = filesCache.filter(it => (it.path||'').toLowerCase().includes(f) || (it.owner||'').toLowerCase().includes(f));
     renderFilesList(filtered);
   }

   async function loadInfoLogs(){
     currentInfoView = 'logs';
     const content = el('info-content'); if (!content) return; content.innerHTML = '<div class="muted">正在加载操作日志...</div>';
     try{
       const url = base + '/api/admin/logs';
       const data = await fetchJson(url);
       renderLogs(data.items || []);
     }catch(e){
       if (e && e.code === 404) {
         content.innerHTML = `<div class="muted">后端未实现接口 <strong>/api/admin/logs</strong> (404)。<br>请求 URL: ${escapeHtml(e.url)} <br><button id="logsRetry" class="btn">重试</button></div>`;
         const btn = el('logsRetry'); if (btn) btn.addEventListener('click', loadInfoLogs);
         console.debug('admin logs endpoint 404:', e.url);
         try { showAdminBanner(e.url); } catch (ex) { console.debug('showAdminBanner failed', ex); }
       } else {
         content.innerHTML = '<div class="muted">加��失败</div>';
       }
      }
   }

    // --- 替换 renderLogs 函数 ---
   function renderLogs(items){
     const c = el('info-content'); if (!c) return; c.innerHTML = '';
     if (!items || items.length===0){
          c.innerHTML = '<div class="muted" style="text-align:center; padding:40px;">暂无操作日志</div>';
          return;
      }
     const wrapper = document.createElement('div');
     wrapper.className = 'data-table-wrapper';
      const table = document.createElement('table');
     table.className = 'data-table';
     table.innerHTML = `
       <thead>
         <tr>
           <th style="width:180px">发生时间</th>
           <th style="width:120px">操作用户</th>
           <th style="width:100px">动作类型</th>
           <th>详细信息</th>
         </tr>
       </thead>`;
     const tb = document.createElement('tbody');
     items.forEach(it=>{
       // 字段兼容处理
       const timeText = it.timeText || (typeof it.time === 'number' ? new Date(it.time).toLocaleString() : (it.raw ? it.raw.split('\t')[0] : ''));
       const user = it.user || it['user'] || '未知';
       const action = it.action || it['action'] || 'Info';
       const detail = it.detail || it.path || it.info || it['detail'] || '-';
       if (!action && !detail) return; // 跳过空行
        const badgeClass = getActionBadgeClass(action); // 获取颜色
        const tr = document.createElement('tr');
       tr.innerHTML = `
         <td class="text-muted">${escapeHtml(timeText||'')}</td>
         <td><span style="font-weight:500; color:#333;">${escapeHtml(user)}</span></td>
         <td><span class="badge ${badgeClass}">${escapeHtml(action)}</span></td>
         <td class="text-mono" style="font-size:12px; color:#555;">${escapeHtml(detail)}</td>
       `;
       tb.appendChild(tr);
     });
     table.appendChild(tb);
      wrapper.appendChild(table);
     // 将组装好的表格插入容器，否则界面不会显示任何日志
     c.appendChild(wrapper);
   }

   function openAnnEditor(){
     currentInfoView = 'ann';
     const modal = el('adminModal'); if (!modal) return;
     modal.style.display='flex';
     el('adminModalBody').innerHTML = '<div class="notice">公告内容编辑器（可在此处管理公告）</div>';
     el('modalOk').onclick = ()=>{ alert('编辑功能请从公告管理面操作'); modal.style.display='none'; };
   }

   // ---------------- User management ----------------
   let usersCache = [];
   let useMockUsers = false;
   function enableMockUsers() {
     useMockUsers = true;
     usersCache = [
       { username: 'user1', email: 'user1@example.com', role: 'user', createdAt: Date.now() - 3600*1000 },
       { username: 'admin', email: 'admin@example.com', role: 'admin', createdAt: Date.now() - 86400*1000 }
     ];
     renderUsers(usersCache);
   }
    async function loadUsers(){
      try{
        const url = base + '/api/admin/users';
        const data = await fetchJson(url);
        // Backend should return { users: [...] } on success. Some error cases return { success: false, message: '...' }
       if (data && data.success === false) {
         const outErr = el('userList'); if (outErr) outErr.innerHTML = `<div class="muted">后端返回错误: ${escapeHtml(String(data.message||'未知错误'))}</div>`;
         // Try to show admin banner and fallback to mock data
         try { showAdminBanner(url); } catch(e) { console.debug(e); }
         enableMockUsers();
         return;
       }
       usersCache = data.users || [];
       renderUsers(usersCache);
      }catch(e){
       const out = el('userList'); if (out) {
         if (e && e.code === 404) {
           out.innerHTML = `<div class="muted">后端未实现接口 <strong>/api/admin/users</strong> (404)。<br>请求 URL: ${escapeHtml(e.url)} <br><button id="usersRetry" class="btn">重试</button> <button id="usersMock" class="btn">使用本地模拟数据</button></div>`;
           const btn = el('usersRetry'); if (btn) btn.addEventListener('click', loadUsers);
           const mock = el('usersMock'); if (mock) mock.addEventListener('click', enableMockUsers);
           console.debug('admin users endpoint 404:', e.url);
         } else {
           out.innerHTML = '<div class="muted">加载用户失败</div>';
         }
         // 自动启用本地模拟用户数据
         enableMockUsers();
         const out2 = el('userList'); if (out2) {
           const note = document.createElement('div'); note.className = 'muted'; note.style.marginTop = '8px'; note.textContent = '已自动启用本地模拟用户数据以便演示（API 未实现）。'; out2.insertBefore(note, out2.firstChild);
         }
         try { showAdminBanner(e.url); } catch (ex) { console.debug('showAdminBanner failed', ex); }
       }
      }
    }
     function renderUsers(list){
     const out = el('userList'); if (!out) return; out.innerHTML = '';
     if (!list || list.length === 0) { out.innerHTML = '<div class="muted">暂无用户</div>'; return; }
     const wrapper = document.createElement('div');
     wrapper.className = 'data-table-wrapper';
     const table = document.createElement('table');
     table.className = 'data-table';
     table.innerHTML = '<thead><tr><th>用户名</th><th>角色</th><th>创建时间</th><th style="width:150px; text-align:center;">操作</th></tr></thead>';
     const tb = document.createElement('tbody');
     list.forEach(u => {
       const tr = document.createElement('tr');
       // if username is 'root' treat it as admin for display purposes
       const uname = (u.username || '').toString();
       const avatarUrl = u.avatar || `https://i.pravatar.cc/40?u=${encodeURIComponent(uname)}`;
      // Normalize role information from possible shapes: u.role (string) or u.roles (array/string)
      let roleHint = '';
      if (typeof u.role === 'string') roleHint = u.role;
      else if (Array.isArray(u.roles)) roleHint = u.roles.join(',');
      else if (typeof u.roles === 'string') roleHint = u.roles;
      const roleLower = (roleHint || '').toString().toLowerCase();
      // Treat username 'root' or any role hint containing admin/root/super as admin
      const isRootUser = uname && uname.toLowerCase() === 'root';
      const isRoleAdmin = ['admin','administrator','root','super'].some(k => roleLower.includes(k));
      const displayRole = (isRootUser || isRoleAdmin) ? 'admin' : (u.role || 'user');
      const roleBadge = `<span class="badge ${displayRole === 'admin' ? 'badge-red' : 'badge-blue'}">${escapeHtml(displayRole)}</span>`;
       tr.innerHTML = `
        <td>
            <div class="file-cell">
                <img src="${avatarUrl}" alt="avatar" style="width:32px; height:32px; border-radius:50%; object-fit:cover;">
                <span class="file-name">${escapeHtml(uname)}</span>
            </div>
        </td>
        <td>${roleBadge}</td>
        <td class="text-muted">${new Date(u.createdAt||0).toLocaleString()}</td>
        <td style="text-align:center;">
            <button class="btn btn-xs" data-name="${escapeHtml(uname)}" data-action="imp">重置密码</button>
            <button class="btn btn-xs btn-ghost-danger" data-name="${escapeHtml(uname)}" data-action="del">删除</button>
        </td>`;
       tb.appendChild(tr);
     });
     table.appendChild(tb);
     wrapper.appendChild(table);
     out.appendChild(wrapper);
     out.querySelectorAll('button[data-action="imp"]').forEach(b=>b.addEventListener('click', ()=>{ const n=b.getAttribute('data-name'); if (!confirm('确定重置用户 '+n+' 的密码吗?')) return; adminResetPwd(n); }));
     out.querySelectorAll('button[data-action="del"]').forEach(b=>b.addEventListener('click', ()=>{ const n=b.getAttribute('data-name'); if (!confirm('确定删除用户 '+n+' ?')) return; adminDeleteUser(n); }));
   }

   function renderUsersFilter(q){
     const f = q && q.trim().toLowerCase();
     if (!f) return renderUsers(usersCache);
     const filtered = usersCache.filter(u => (u.username||'').toLowerCase().includes(f));
     renderUsers(filtered);
   }

   async function openNewUserModal(){
     const modal = el('adminModal'); if (!modal) return;
     modal.style.display='flex';
     el('adminModalBody').innerHTML = `<div style="display:flex;flex-direction:column;gap:8px;"><input id="nuName" placeholder="用户名" style="padding:8px;border:1px solid #ddd;border-radius:6px;"><input id="nuEmail" placeholder="邮箱" style="padding:8px;border:1px solid #ddd;border-radius:6px;"><input id="nuPwd" placeholder="初始密码" style="padding:8px;border:1px solid #ddd;border-radius:6px;"></div>`;
     el('modalOk').onclick = async ()=>{ const name=el('nuName').value.trim(); const email=el('nuEmail').value.trim(); const pwd=el('nuPwd').value.trim(); if (!name||!pwd) { alert('用户名和密码为必填'); return; } await adminCreateUser({ username:name, email:email, password:pwd }); modal.style.display='none'; };
   }

   async function adminCreateUser(payload){
     try{
       const url = base + '/api/admin/users';
       if (useMockUsers) {
         usersCache.unshift({ username: payload.username, email: payload.email || '', role: payload.role || 'user', createdAt: Date.now() });
         renderUsers(usersCache);
       } else {
         await fetchJson(url, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload)});
         await loadUsers();
       }
     }catch(e){ alert(e.message || (e.code===404?('接口未实现: '+e.url):e)); }
   }

   async function adminResetPwd(username){
     try{
       const url = base + '/api/admin/users/reset?username=' + encodeURIComponent(username);
       if (useMockUsers) { alert('（模拟）重置成功'); }
       else { await fetchJson(url, { method:'POST' }); alert('重置成功'); }
     }catch(e){ alert(e.message || (e.code===404?('接口未实现: '+e.url):e)); }
   }

   async function adminDeleteUser(username){
     try{
       const url = base + '/api/admin/users?username=' + encodeURIComponent(username);
       if (useMockUsers) { usersCache = usersCache.filter(u=>u.username!==username); renderUsers(usersCache); }
       else { await fetchJson(url, { method:'DELETE' }); await loadUsers(); }
     }catch(e){ alert(e.message || (e.code===404?('接口未实现: '+e.url):e)); }
   }

   async function adminDeleteFile(path){
     try{
       const url = base + '/api/admin/files?path=' + encodeURIComponent(path);
       if (useMockFiles) {
         // remove from filesCache and re-render current view
         filesCache = (filesCache || []).filter(f => (f.path||'') !== path);
         renderFilesList(filesCache);
         alert('（模拟）删除成功');
       } else {
         await fetchJson(url, { method: 'DELETE' });
         // reload current directory
         await loadInfoFiles(currentFilePath);
         alert('删除成功');
       }
     }catch(e){ alert('删除失败: ' + (e.message || (e.code===404?('接口未实现: '+e.url):e))); }
   }

   // --- 公告板视图逻辑 ---

   // 加载公告数据供公告板使用 (复用 API，但使用不同的渲染器)
   async function loadBulletinBoard(){
     const out = el('boardList'); if (!out) return;
     out.innerHTML = '<div class="muted">正在刷新公告...</div>';
     try{
       const url = base + '/api/admin/announcements';
       const data = await fetchJson(url);
       const list = data.items || [];
       // 按时间倒序排序
       list.sort((a,b) => (b.createdAt||0) - (a.createdAt||0));
       renderBulletinBoard(list);
     }catch(e){
       // 同样支持模拟数据 fallback
       if (e && e.code === 404) {
         // 如果启用了模拟，直接使用模拟数据
         if (typeof useMockAnnouncements !== 'undefined' && useMockAnnouncements) {
            // 确保有模拟数据 (引用全局变量 announcements)
            if (typeof announcements !== 'undefined') {
                renderBulletinBoard(announcements);
                return;
            }
         }
         out.innerHTML = `<div class="muted">获取公告失败或接口不存在。<br><button class="btn" onclick="loadBulletinBoard()">重试</button></div>`;
       } else {
         out.innerHTML = '<div class="muted">加载失败</div>';
       }
     }
   }

   // 渲染漂亮的公告卡片列表
   function renderBulletinBoard(list) {
     const out = el('boardList'); if (!out) return;
     out.innerHTML = '';

     if (!list || list.length === 0) {
       out.innerHTML = `
         <div style="text-align:center; padding:40px; background:#fff; border-radius:8px; border:1px dashed #ddd;">
           <div style="font-size:40px; margin-bottom:10px;">📭</div>
           <div class="muted">暂时没有系统公告</div>
         </div>`;
       return;
     }

     // 计算“最新”的时间阈值（例如 3 天内）
     const threeDaysAgo = Date.now() - (3 * 24 * 60 * 60 * 1000);

     list.forEach(item => {
       const card = document.createElement('div');
       card.className = 'board-card';

       const timeObj = new Date(item.createdAt || Date.now());
       const timeStr = timeObj.toLocaleString();
       const isNew = (item.createdAt || 0) > threeDaysAgo;

       const newBadgeHtml = isNew ? '<span class="board-badge-new">NEW</span>' : '';
       const author = item.author || '系统管理员';

       card.innerHTML = `
         <div class="board-header">
           <h3 class="board-title">
             ${escapeHtml(item.title)}
             ${newBadgeHtml}
           </h3>
           <span class="board-meta" title="${timeStr}">
             🕒 ${formatTimeAgo(timeObj)}
           </span>
         </div>
         <div class="board-content">${escapeHtml(item.content)}</div>
         <div class="board-footer">
           <span>👤 发布者: ${escapeHtml(author)}</span>
           <span>📅 ${timeStr}</span>
         </div>
       `;
       out.appendChild(card);
     });
   }

   // 简单的相对时间格式化辅助函数 (例如: "2小时前")
   function formatTimeAgo(date) {
     const now = new Date();
     const diff = Math.floor((now - date) / 1000); // seconds
     if (diff < 60) return '刚刚';
     if (diff < 3600) return Math.floor(diff/60) + '分钟前';
     if (diff < 86400) return Math.floor(diff/3600) + '小时前';
     if (diff < 259200) return Math.floor(diff/86400) + '天前';
     return date.toLocaleDateString();
   }


   // init
   document.addEventListener('DOMContentLoaded', init);
})();

// dev-20261112-007：独立评审交互。只查找既有Java计算器生成的结果，不在浏览器重写耗用算法。
export const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const clone = value => structuredClone(value);
const numberText = value => String(Number(value));
export function validNumber(value, places, allowZero = false) {
  if (value === '' || value == null) return false;
  const number = Number(value);
  return Number.isFinite(number) && (allowZero ? number >= 0 : number > 0) &&
    new RegExp(`^\\d+(?:\\.\\d{1,${places}})?$`).test(String(value));
}
export function getQuote(row, quantity, data) {
  if (!validNumber(row.module, 4) || !validNumber(row.base, 4) ||
      !validNumber(row.loss, 2, true) || Number(row.loss) > 100 ||
      !validNumber(row.minimum ?? 0, 4, true) || !Number.isInteger(Number(quantity)) || Number(quantity) <= 0) return null;
  return data.quotes[[row.unit, row.base, row.module, row.loss, row.minimum ?? 0, quantity]
    .map((value, index) => index ? numberText(value) : value).join('|')] ?? null;
}
export function getPitchQuote(row, quantity, data) {
  if (!validNumber(row.pitch, 2) || !validNumber(row.perJump, 1) ||
      !Number.isInteger(Number(row.perJump)) || !Number.isInteger(Number(quantity)) || Number(quantity) <= 0) return null;
  return data.pitchQuotes[[row.unit, row.width, row.pitch, row.perJump, quantity]
    .map((value, index) => index ? numberText(value) : value).join('|')] ?? null;
}
export function adoptPitch(row, quantity, data) {
  const quote = getPitchQuote(row, quantity, data);
  if (!quote?.baseQtyStorableInBom || quote.baseQtySuggested == null) return false;
  row.module = quote.moduleQtySuggested;
  row.base = quote.baseQtySuggested;
  return true;
}

export function mountReview(doc, data) {
  const $ = id => doc.getElementById(id);
  const materials = new Map(data.materials.map(material => [material.id, material]));
  const initial = data.boms.map(bom => {
    const output = materials.get(bom.outputId);
    return { ...clone(bom), outputCode: output?.code ?? data.rootProduct.code,
      unit: output?.unit ?? data.rootProduct.unit, spec: output?.spec ?? '成品', name: output?.name ?? data.rootProduct.name ?? bom.name,
      quantity: 100, dirty: false, remark: bom.remark ?? '', rows: bom.rows.map(row => makeRow(row)) };
  });
  function makeRow(row) {
    const material = materials.get(row.materialId);
    return { ...clone(row), ...material, id: row.id, materialId: row.materialId,
      module: row.module ?? '', base: row.base ?? '', loss: row.loss ?? 0, minimum: row.minimum ?? 0,
      pitch: Number.parseFloat(row.pitch) || '', perJump: 1, sub: row.sub ?? null,
      rollLength: material?.length ? material.length / 1000 : '' };
  }
  const drafts = new Map(initial.map(bom => [bom.id, clone(bom)]));
  const saved = new Map();
  let stack = [drafts.get(2)], selectedRow = null, selectedId = null, replacing = null, helperId = null, nextId = -1, timer;
  const current = () => stack.at(-1);
  function toast(message) { $('toast').textContent = message; clearTimeout(timer); timer = setTimeout(() => { $('toast').textContent = ''; }, 5500); }
  function markDirty() { current().dirty = true; }
  const unitLabel = unit => unit === 'M2' ? '㎡' : unit === 'M' ? '米' : unit;
  const isRolling = row => stack.length > 1 && current().unit === 'PCS' && data.rollingInputs.includes(row.materialId);
  function cellResult(row, field) {
    const quote = getQuote(row, current().quantity, data);
    return quote ? `<span class="${field === 'total' ? 'result' : 'number-text'}">${escapeHtml(quote[field])}</span>`
      : '<span class="result pending">待试算</span>';
  }
  function render() {
    const bom = current(), root = stack.length === 1;
    $('title').textContent = root ? 'YL1025PWOO · 产品BOM' : `${bom.outputCode} · 制造BOM`;
    $('context').textContent = `${bom.code} / ${bom.version} · ${root ? '成品直接用料' : '只维护本层产出与投入'}`;
    $('path').innerHTML = stack.map((frame, index) => `${index ? '<span>›</span>' : ''}<button class="link" data-path="${index}">${escapeHtml(index ? frame.outputCode + ' ' + frame.version : '产品BOM V2')}</button>`).join('');
    $('path').querySelectorAll('button').forEach(button => { button.onclick = () => navigate(Number(button.dataset.path)); });
    $('back').hidden = root; $('adopt').hidden = root;
    $('identity').innerHTML = [['产出物料', bom.outputCode], ['名称', bom.name], ['规格', bom.spec], ['产出单位', unitLabel(bom.unit)]]
      .map(([label, value]) => `<div><span class="label">${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`).join('');
    $('output-unit').textContent = unitLabel(bom.unit);
    $('table-title').textContent = root ? '成品直接用料' : '本层投入材料';
    $('quantity').value = bom.quantity;
    const conversions = $('show-conversions').checked;
    $('columns').innerHTML = `<tr><th>物料名称／编码</th><th>规格</th><th>单位</th><th>模数</th><th>基数</th><th>损耗 %</th><th>每件用量</th><th>总需求</th>${conversions ? '<th>折算米</th><th>预计卷数</th>' : ''}<th>操作</th></tr>`;
    $('rows').innerHTML = bom.rows.map((row, index) => {
      const quote = getQuote(row, bom.quantity, data), length = quote?.lengths?.[numberText(row.width)];
      const rolls = length?.rolls?.[numberText(row.rollLength)];
      const input = (field, places, zero = false) => `<input class="num ${validNumber(row[field], places, zero) ? '' : 'invalid'}" type="number" min="${zero ? '0' : '0.0001'}" step="${field === 'loss' ? '0.01' : '0.0001'}" value="${escapeHtml(row[field])}" data-row="${index}" data-key="${field}" aria-label="${escapeHtml(row.code)}${field}">`;
      return `<tr data-material="${row.materialId}"><td>${escapeHtml(row.name)}<span class="code">${escapeHtml(row.code)}</span></td><td>${escapeHtml(row.spec)}</td><td>${unitLabel(row.unit)}</td><td>${input('module', 4)}</td><td>${input('base', 4)}</td><td>${input('loss', 2, true)}</td><td>${cellResult(row, 'each')}</td><td>${cellResult(row, 'total')}</td>${conversions ? `<td>${length ? escapeHtml(length.metres) : '—'}</td><td>${isRolling(row) && rolls != null ? `${escapeHtml(rolls)} <span class="muted">按${escapeHtml(row.rollLength)}m参考</span>` : '—'}</td>` : ''}<td><div class="row-tools"><button class="link" data-scheme="${index}">${row.sub ? '查看／更换制造方案' : '选择／新建制造方案'}</button>${isRolling(row) ? `<button class="link" data-pitch="${index}">跳距辅助定额${row.pitch ? ' · '+escapeHtml(row.pitch)+'mm' : ''}</button>` : ''}<button class="link" data-replace="${index}">重新选料</button><button class="link" data-remove="${index}" style="color:#8996a7">移除</button></div></td></tr>`;
    }).join('') || '<tr><td colspan="11" class="empty">添加投入材料，填写模数、基数和损耗率。</td></tr>';
    $('rows').querySelectorAll('input').forEach(input => {
      input.onchange = () => {
        const row = bom.rows[Number(input.dataset.row)];
        if (String(row[input.dataset.key]) === input.value) return;
        row[input.dataset.key] = input.value;
        markDirty(); render();
      };
    });
    $('rows').querySelectorAll('[data-scheme]').forEach(button => { button.onclick = () => choose(bom.rows[Number(button.dataset.scheme)]); });
    $('rows').querySelectorAll('[data-pitch]').forEach(button => { button.onclick = () => { helperId = bom.rows[Number(button.dataset.pitch)].id; renderPitch(); }; });
    $('rows').querySelectorAll('[data-replace]').forEach(button => { button.onclick = () => openMaterials(Number(button.dataset.replace)); });
    $('rows').querySelectorAll('[data-remove]').forEach(button => { button.onclick = () => {
      if (!globalThis.confirm('移除此层材料？独立制造方案仍保留。')) return;
      bom.rows.splice(Number(button.dataset.remove), 1); markDirty(); render();
    }; });
    $('remark').value = bom.remark;
    $('save-state').textContent = bom.dirty ? '有未保存的评审修改' : saved.has(bom.id) ? '本层评审草稿已保留' : '预览初始值';
    $('save-state').className = bom.dirty ? 'dirty' : 'muted';
    $('boundary').textContent = root ? '下层制造方案独立保存；采用后还需保存本层引用。' : '保存本层后可采用返回；取消上层不会删除独立保存的方案。';
    const minima = bom.rows.filter(row => Number(row.minimum) > 0);
    $('advanced-summary').textContent = minima.length ? `· ${minima.length}行已设最低投料` : '· 可选';
    $('advanced-fields').innerHTML = bom.rows.map((row, index) => `<label>${escapeHtml(row.code)} 最低投料 <input class="num wide" data-minimum="${index}" type="number" min="0" step="0.0001" value="${escapeHtml(row.minimum)}"></label>${isRolling(row) ? `<label>参考卷长 <input class="num" data-roll="${index}" type="number" min="0.01" step="0.01" value="${escapeHtml(row.rollLength)}" placeholder="选填"> 米</label>` : ''}`).join('');
    $('advanced-fields').querySelectorAll('input').forEach(input => { input.onchange = () => {
      if (input.dataset.minimum !== undefined) {
        const row = bom.rows[Number(input.dataset.minimum)];
        if (String(row.minimum) === input.value) return;
        row.minimum = input.value; markDirty();
      } else {
        const row = bom.rows[Number(input.dataset.roll)];
        if (String(row.rollLength) === input.value) return;
        row.rollLength = input.value;
      }
      render();
    }; });
    renderPitch();
  }
  function renderPitch() {
    const row = current().rows.find(item => item.id === helperId && isRolling(item));
    $('pitch-helper').hidden = !row;
    if (!row) return;
    const quote = getPitchQuote(row, current().quantity, data);
    $('pitch-helper').innerHTML = `<div class="helper-title"><h2>${escapeHtml(row.code)} · 跳距辅助定额</h2><button id="close-helper" class="link">收起</button></div><div class="helper-fields"><label><span>卷宽</span><strong>${escapeHtml(row.width)} mm</strong></label><label><span>跳距 mm</span><input id="pitch" class="num wide" type="number" min="0.01" step="0.01" value="${escapeHtml(row.pitch)}" list="pitch-examples"></label><datalist id="pitch-examples"><option value="78"><option value="80"><option value="80.5"><option value="100"></datalist><label><span>每跳产出</span><input id="per-jump" class="num" type="number" min="1" step="1" value="${escapeHtml(row.perJump)}"> PCS</label></div><div class="helper-result"><span>按当前 ${escapeHtml(current().quantity)} 件，理论投入</span><strong id="pitch-total">${quote ? `${escapeHtml(quote.displayConsumption)} ${unitLabel(row.unit)}` : '待试算'}</strong><button id="apply-pitch" class="primary" ${quote?.baseQtyStorableInBom ? '' : 'disabled'}>确认采用定额</button><span id="pitch-note" class="muted">${quote ? quote.baseQtyStorableInBom ? `采用后：模数 ${quote.moduleQtySuggested}，基数 ${quote.baseQtySuggested}。额外损耗仍按材料行计算。` : '该比例不能直接按现有精度保存，请调整定额表达；不自动舍入。' : '当前组合未收录，正式接入后由后端即时试算。'}</span></div><p class="muted">跳距可按片长预填，已有方案保留原值；修改跳距不会直接覆盖人工定额，确认采用后才回填。此处不含额外损耗和最低投料。</p>`;
    $('close-helper').onclick = () => { helperId = null; renderPitch(); };
    for (const [id, key] of [['pitch', 'pitch'], ['per-jump', 'perJump']]) $(''+id).onchange = event => {
      if (String(row[key]) === event.target.value) return;
      row[key] = event.target.value; markDirty(); render();
    };
    $('apply-pitch').onclick = () => {
      if (!adoptPitch(row, current().quantity, data)) return;
      markDirty(); render(); toast('定额已回填当前评审草稿；请保存本层后再采用。');
    };
  }
  function choose(row) {
    selectedRow = row;
    const options = [...drafts.values()].filter(bom => bom.outputId === row.materialId && bom.outputType === 'MATERIAL');
    selectedId = options.some(bom => bom.id === row.sub) ? row.sub : options[0]?.id ?? null;
    $('choice-title').textContent = `${row.code} · 选择制造方案`;
    $('choices').innerHTML = options.map(bom => `<label class="choice"><input type="radio" name="scheme" value="${bom.id}" ${bom.id === selectedId ? 'checked' : ''}><span><strong>${escapeHtml(bom.code)} / ${escapeHtml(bom.version)}</strong><span class="muted">产出 ${escapeHtml(bom.outputCode)} · ${unitLabel(bom.unit)}；${bom.rows.length}行投入${bom.rows[0]?.pitch ? '；原跳距'+escapeHtml(bom.rows[0].pitch)+'mm' : ''}</span></span></label>`).join('') || '<div class="empty">尚无制造方案，可进入新建。</div>';
    $('choices').querySelectorAll('input').forEach(input => { input.onchange = () => { selectedId = Number(input.value); }; });
    $('open-scheme').disabled = !options.length;
    $('chooser').showModal();
  }
  function enter(bom) { $('chooser').close(); stack.push(bom); bom.parentRow = selectedRow; helperId = null; $('advanced').open = false; render(); }
  function navigate(index) {
    if (index >= stack.length - 1) return;
    if (current().dirty && !globalThis.confirm('本层修改仍保留在当前页面，继续返回？')) return;
    stack = stack.slice(0, index + 1); helperId = null; $('advanced').open = false; render();
  }
  function openMaterials(index = null) {
    replacing = index;
    $('material-options').innerHTML = [...materials.values()].map(material => `<button class="choice" data-select-material="${material.id}"><span><strong>${escapeHtml(material.name)}</strong><span class="muted">${escapeHtml(material.code)} · ${escapeHtml(material.spec)} · ${unitLabel(material.unit)}</span></span></button>`).join('');
    $('material-options').querySelectorAll('button').forEach(button => { button.onclick = () => {
      const previous = replacing == null ? null : current().rows[replacing];
      const materialId = Number(button.dataset.selectMaterial);
      const row = makeRow({ id: previous?.id ?? nextId--, materialId, module: '', base: '', loss: 0, minimum: 0, sub: null });
      if (isRolling(row)) row.pitch = materials.get(current().outputId)?.length ?? '';
      if (replacing == null) current().rows.push(row); else current().rows[replacing] = row;
      markDirty(); $('material-chooser').close(); helperId = null; render();
    }; });
    $('material-chooser').showModal();
  }
  $('show-conversions').onchange = render;
  $('quantity').onchange = event => {
    if (String(current().quantity) === event.target.value) return;
    current().quantity = event.target.value; render();
  };
  $('remark').oninput = () => { current().remark = $('remark').value; markDirty(); $('save-state').textContent = '有未保存的评审修改'; };
  $('add-material').onclick = () => openMaterials();
  $('close-materials').onclick = () => $('material-chooser').close();
  $('close-choice').onclick = () => $('chooser').close();
  $('open-scheme').onclick = () => { const bom = drafts.get(selectedId); if (bom) enter(bom); };
  $('new-scheme').onclick = () => {
    const output = materials.get(selectedRow.materialId);
    const bom = { id: nextId--, code: `${output.code}-MFG`, name: output.name, version: '新评审草稿',
      outputType: 'MATERIAL', outputId: output.id, outputCode: output.code, spec: output.spec, unit: output.unit,
      rows: [], remark: '', dirty: true, quantity: current().quantity };
    drafts.set(bom.id, bom); enter(bom);
  };
  $('back').onclick = () => navigate(stack.length - 2);
  $('save').onclick = () => {
    const bom = current();
    if (!bom.rows.length || bom.rows.some(row => !validNumber(row.base, 4) || !validNumber(row.module, 4) ||
      !validNumber(row.loss, 2, true) || Number(row.loss) > 100 || !validNumber(row.minimum, 4, true))) {
      toast('请填写有效的模数、基数、损耗率和最低投料。'); return;
    }
    saved.set(bom.id, clone({ ...bom, parentRow: undefined, dirty: false })); bom.dirty = false; render();
    toast('已保留本层评审草稿；未写入正式BOM。');
  };
  $('adopt').onclick = () => {
    const bom = current(); if (bom.dirty) { toast('请先保存本层草稿，再采用。'); return; }
    bom.parentRow.sub = bom.id; stack.pop(); markDirty(); helperId = null; render(); toast('已回填上层草稿，请保存上层引用。');
  };
  $('cancel').onclick = () => {
    if (!globalThis.confirm('取消本层修改？独立保存的下层方案仍保留。')) return;
    const bom = current(), source = saved.get(bom.id) ?? initial.find(item => item.id === bom.id);
    if (source) { const parent = bom.parentRow; Object.assign(bom, clone(source), { parentRow: parent, dirty: false }); }
    else { drafts.delete(bom.id); stack.pop(); }
    helperId = null; render();
  };
  render();
  return { current, drafts, render };
}

if (typeof document !== 'undefined' && document.getElementById('bom-review')) {
  fetch(new URL('./review-data.json', import.meta.url)).then(response => {
    if (!response.ok) throw new Error('预览数据未加载'); return response.json();
  }).then(data => mountReview(document, data)).catch(error => {
    document.getElementById('title').textContent = '预览暂未加载';
    document.getElementById('context').textContent = error.message;
  });
}

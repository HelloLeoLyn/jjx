const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { spawnSync } = require('node:child_process')
const { pathToFileURL } = require('node:url')
const ts = require('typescript')
const vue = require('vue')
const { renderToString } = require('vue/server-renderer')
const { parse, compileScript, compileStyle } = require('vue/compiler-sfc')
const directory = path.resolve(__dirname, '..')
const compile = text => ts.transpileModule(text, { compilerOptions: {
  module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022,
} }).outputText
function load(text, deps = {}) {
  const state = { exports: {}, require: name => {
    assert.ok(name in deps, `Unexpected dependency: ${name}`)
    return deps[name]
  } }
  vm.runInNewContext(compile(text), state)
  return state.exports
}
function component(filename, deps, scope) {
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const script = compileScript(descriptor, { id: scope, inlineTemplate: true })
  const value = load(script.content, deps).default
  value.__scopeId = 'data-v-' + scope
  const css = descriptor.styles.map(style => compileStyle({ source: style.content,
    filename, id: 'data-v-' + scope, scoped: style.scoped }).code).join('\n')
  return { value, css }
}
const fonts = load(fs.readFileSync(path.join(directory, 'workSpecFont.ts'), 'utf8'))
const source = ts.createSourceFile('docset.ts', fs.readFileSync(path.join(directory, 'docset.ts'), 'utf8'), ts.ScriptTarget.Latest, true)
const functions = source.statements.filter(node => ts.isFunctionDeclaration(node) &&
  ['plain', 'workSpecMaterialSpec', 'workSpecMaterialDimensions', 'workSpecOperationRemark', 'workSpecStepSubscript'].includes(node.name?.text))
  .map(node => node.getText(source)).join('\n')
const formatters = load(fs.readFileSync(path.join(directory, '../../../../utils/bomQuantity.ts'), 'utf8') + '\n' + functions)
const groups = ['PANEL', 'UP_LINE', 'DOWN_LINE'].map((value, i) => ({ value, label: ['面板', '上线', '下线'][i] }))
const icon = component(path.join(directory, '../../../../components/SvgIcon/index.vue'), { vue }, 'flow-icon')
const { embossFields } = load(fs.readFileSync(path.join(directory, '../../../../api/product/workSpec.ts'), 'utf8'), { '@/utils/request': {} })
const paper = component(path.join(directory, 'WorkSpecPaper.vue'), {
  vue, '@/components/SvgIcon/index.vue': { __esModule: true, default: icon.value },
  '@/api/product/workSpec': { embossFields },
  './docset': { ...formatters, flowGroups: groups }, './workSpecFont': fonts,
  './useWorkSpecOverflow': { useWorkSpecOverflow: () => ({ measure() {} }) },
}, 'flow-paper')

test('实际Chrome按A4分页：材料、长备注、流程、结构图和变更不丢失，字号变化重新排版', { timeout: 90000 }, async t => {
  const chrome = process.env.CHROME_BIN || ['C:/Program Files/Google/Chrome/Application/chrome.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium'].find(filename => fs.existsSync(filename))
  if (!chrome) { t.skip('未安装Chrome；设置CHROME_BIN执行DOM分页测试'); return }
  const rows = Array.from({ length: 70 }, (_, i) => ({ itemId: i + 1, materialName: `材料${String(i + 1).padStart(3, '0')}`,
    specification: '110', split: '50mm', moduleQty: 5, baseQty: 2 }))
  const operations = groups.flatMap(group => Array.from({ length: 21 }, (_, i) => ({
    itemId: group.value + i, processCategory: group.value, remark: `${group.label}工序${i + 1}说明`, standardLaborHours: 0.5,
  })))
  const notes = '备注内容保留换行与自由文本<80℃>。\n'.repeat(160)
  const changes = '变更记录不截断。'.repeat(300)
  const svg = 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="skyblue"/></svg>').toString('base64')
  const data = { product: { productName: '分页验证', productCode: 'GF404P000' },
    routing: { items: operations }, workSpec: { emboss: { setupHeight: '自由填写', requiredHeight: '0', upperTemperature: '80℃',
      lowerTemperature: '<80℃>', pressTime: '', holdTime: ' ' }, requirementsColor: '#1565c0', issueUnit: '工程部', approved: false } }
  const page = { rows, engineeringNotes: notes, diePositionRows: rows.map((_, i) => `D${i + 1}`),
    changeLines: [{ text: changes, color: '#c00' }], groups: groups.map(group => ({ ...group,
      rows: operations.filter(row => row.processCategory === group.value) })) }
  const html = await renderToString(vue.createSSRApp(paper.value, { data, page, pageIndex: 0, pageCount: 1,
    specTemplate: 'B', fontSettings: fonts.defaultWorkSpecFontSettings(), structureImage: svg, structureCaption: '结构图验证' }))
  const helper = compile(fs.readFileSync(path.join(directory, 'workSpecFlowPagination.ts'), 'utf8'))
  // 独立文件和浏览器配置目录，不依赖或操作项目服务。
  const temporaryRoot = path.resolve(directory, '../../../../../../.tmp')
  fs.mkdirSync(temporaryRoot, { recursive: true })
  const temporary = fs.mkdtempSync(path.join(temporaryRoot, 'dev-20261021-007-flow-'))
  try {
    const fixture = path.join(temporary, 'fixture.html')
    fs.writeFileSync(fixture, `<!doctype html><meta charset="utf-8"><style>body{margin:0}#source,#pages{width:192mm}${paper.css}${icon.css}
      @media print{@page{size:A4 portrait;margin:9mm}#source,#result{display:none}#pages>article{break-after:page}#pages>article:last-child{break-after:auto}}
      </style>
      <div id="source">${html}</div><div id="pages"></div><pre id="result"></pre><script>
      const exports={};${helper}
      window.onload=async()=>{try{
        await document.fonts.ready;await Promise.all(Array.from(document.images,img=>img.decode()));
        const source=document.querySelector('#source article'), original=source.outerHTML;
        const expectedNotes=source.querySelector('.material-remarks-content')?.textContent || '';
        const expectedOverviewNotes=source.querySelector('.overview-notes').textContent;
        const structureHeight=source.querySelector('.overview-structure-content').getBoundingClientRect().height;
        const expectedRemarks=Array.from(source.querySelectorAll('.operation-remark'),el=>el.textContent);
        function run(){
          const pages=exports.paginateWorkSpecFlow(source),target=document.querySelector('#pages');
          target.innerHTML=pages.map((html,i)=>exports.workSpecFlowPageHtml(html,i,pages.length)).join('');
          const articles=Array.from(target.children);
          return {count:pages.length, fits:articles.every(el=>el.scrollHeight<=el.clientHeight+1),
            materials:Array.from(target.querySelectorAll('.material-name'),el=>el.textContent).filter(Boolean),
            dies:Array.from(target.querySelectorAll('.die-position'),el=>el.textContent).filter(Boolean),
            notes:Array.from(target.querySelectorAll('.material-remarks-content'),el=>el.textContent).join(''),
            remarks:Array.from(target.querySelectorAll('.operation-remark'),el=>el.textContent).filter(Boolean),
            changes:Array.from(target.querySelectorAll('.changes-content'),el=>el.textContent).join(''),
            images:target.querySelectorAll('.structure img').length,
            overviewImages:target.querySelectorAll('.overview-art img').length,
            overviewNotes:Array.from(target.querySelectorAll('.overview-notes'),el=>el.textContent).join(''),
            overviewColors:Array.from(target.querySelectorAll('.overview-notes'),el=>el.style.color),
            overviewValues:Array.from({length:6},(_,index)=>Array.from(target.querySelectorAll('.overview-emboss'),table=>table.rows[index].cells[1].textContent).join('')),
            overviewFirstValues:Array.from(target.querySelector('.overview-emboss').rows,row=>row.cells[1].textContent),
            overviewHeading:Array.from(target.querySelectorAll('.overview-title'),el=>el.textContent).join(''),
            overviewGeometry:(()=>{
              const section=target.querySelector('.spec-overview'),structure=section.querySelector('.overview-structure-content'),conditions=section.querySelector('.overview-conditions'),remarks=section.querySelector('.overview-remarks');
              return {width:section.getBoundingClientRect().width,height:section.getBoundingClientRect().height,
                structureWidth:structure.getBoundingClientRect().width,structureHeight:structure.getBoundingClientRect().height,
                conditionsWidth:conditions.getBoundingClientRect().width,conditionsHeight:conditions.getBoundingClientRect().height,
                conditionsBottomBorder:getComputedStyle(conditions).borderBottomWidth,
                remarksWidth:remarks.getBoundingClientRect().width,remarksHeight:remarks.getBoundingClientRect().height,
                labelsFit:Array.from(section.querySelectorAll('.overview-emboss-label')).every(el=>el.scrollWidth<=el.clientWidth+1),
                notesClipped:section.querySelector('.overview-notes').scrollHeight>section.querySelector('.overview-notes').clientHeight,
                rows:Array.from(section.querySelectorAll('.overview-emboss tr'),el=>el.getBoundingClientRect().height)};
            })(),
            flowHeadings:Array.from(target.querySelectorAll('.flows thead tr'),tr=>Array.from(tr.cells,el=>el.textContent.trim())),
            firstFlowRow:Array.from(target.querySelector('.flows tbody tr').cells,el=>el.textContent.trim()),
            flowHeaderGeometry:Array.from(target.querySelector('.flows thead tr').cells,el=>({top:el.getBoundingClientRect().top,width:el.getBoundingClientRect().width})),
            totalImages:target.querySelectorAll('img').length,
            structureHeading:target.querySelector('.structure-art').closest('table').querySelector('thead').textContent,
            upperFirstNumber:source.querySelector('.flows').tBodies[0].rows[8].cells[3].textContent.trim(),
            footers:articles.map(el=>el.querySelector('footer').lastElementChild.textContent),
            order:Array.from(target.querySelectorAll('.spec-overview,.materials,.flows,.hours,.issuance'),el=>el.className),
            colors:Array.from(target.querySelectorAll('.changes-content>div'),el=>el.style.color)};
        }
        const normal=run(),stable=JSON.stringify(normal)===JSON.stringify(run()),unchanged=source.outerHTML===original;
        for(const el of source.querySelectorAll('.material-name')){el.parentElement.style.fontSize='32px';}
        source.style.setProperty('--font-notes','24px');
        source.style.setProperty('--overview-label-width','129px');
        for(const el of source.querySelectorAll('.overview-emboss-label'))el.parentElement.style.fontSize='20px';
        const large=run();
        source.outerHTML=original;
        const resetSource=document.querySelector('#source article');
        // 清空正文仍保留模板的固有空行，不能产生额外空页。
        resetSource.querySelectorAll('.material-name,.cell-single,.die-position,.operation-remark,.material-remarks-content,.changes-content,.overview-notes,.overview-emboss-value').forEach(el=>el.textContent='');
        const boundaryHost=document.createElement('div');boundaryHost.style.width='192mm';document.body.append(boundaryHost);
        const boundarySource=resetSource.cloneNode(true);boundaryHost.append(boundarySource);
        Array.from(boundarySource.querySelectorAll('.materials tbody tr:not(.material-remarks)')).slice(42).forEach(el=>el.remove());
        const emptyRemarks=exports.paginateWorkSpecFlow(boundarySource).length;boundaryHost.remove();
        Array.from(resetSource.querySelectorAll('.materials tbody tr:not(.material-remarks)')).slice(12).forEach(el=>el.remove());
        Array.from(resetSource.querySelectorAll('.flows tbody tr')).slice(14).forEach(el=>el.remove());
        const empty=exports.paginateWorkSpecFlow(resetSource);
        const extremeText='单项超长材料说明。'.repeat(700), extremeOperation='单道工序超长说明。'.repeat(500);
        const extremeEmboss='条件按实物调整，不限制长度。'.repeat(180);
        resetSource.querySelector('.overview-emboss-value').textContent=extremeEmboss;
        resetSource.querySelector('.material-name').textContent=extremeText;
        resetSource.querySelector('.operation-remark').textContent=extremeOperation;
        const extremePages=exports.paginateWorkSpecFlow(resetSource),extremeTarget=document.querySelector('#pages');
        extremeTarget.innerHTML=extremePages.join('');
        const extreme={count:extremePages.length,fits:Array.from(extremeTarget.children).every(el=>el.scrollHeight<=el.clientHeight+1),
          material:Array.from(extremeTarget.querySelectorAll('.material-name'),el=>el.textContent).join(''),
          operation:Array.from(extremeTarget.querySelectorAll('.operation-remark'),el=>el.textContent).join(''),
          emboss:Array.from(extremeTarget.querySelectorAll('.overview-emboss-value'),el=>el.textContent).join(''),
          overviewImages:extremeTarget.querySelectorAll('.overview-art img').length,
          images:extremeTarget.querySelectorAll('.structure img').length};
        document.querySelector('#result').textContent=JSON.stringify({normal,large,stable,unchanged,expectedNotes,expectedRemarks,expectedOverviewNotes,structureHeight,empty:empty.length,emptyRemarks,extreme,extremeText,extremeOperation,extremeEmboss});
      }catch(e){document.querySelector('#result').textContent=JSON.stringify({error:e.stack});}};
      </script>`, 'utf8')
    const result = spawnSync(chrome, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--disable-background-networking', '--user-data-dir=' + path.join(temporary, 'profile'),
      '--dump-dom', '--no-pdf-header-footer', '--print-to-pdf=' + path.join(temporary, 'print.pdf'),
      '--virtual-time-budget=3000', pathToFileURL(fixture).href], { timeout: 65000, encoding: 'utf8', maxBuffer: 12 * 1024 * 1024 })
    assert.ifError(result.error)
    assert.equal(result.status, 0, result.stderr)
    const json = result.stdout.match(/<pre id="result">([\s\S]*?)<\/pre>/)?.[1]
    assert.ok(json, result.stdout.slice(-2000))
    const actual = JSON.parse(json.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&'))
    assert.equal(actual.error, undefined, actual.error)
    assert.equal(actual.stable, true)
    assert.equal(actual.unchanged, true)
    assert.equal(actual.structureHeight, 32 + 6 * 22)
    assert.equal(actual.empty, 2)
    assert.ok(actual.emptyRemarks >= 2)
    assert.equal(actual.extreme.fits, true)
    assert.equal(actual.extreme.material, actual.extremeText)
    assert.equal(actual.extreme.operation, actual.extremeOperation)
    assert.equal(actual.extreme.images, 1)
    assert.equal(actual.extreme.overviewImages, 1)
    assert.equal(actual.extreme.emboss, actual.extremeEmboss)
    const pdf = fs.readFileSync(path.join(temporary, 'print.pdf')).toString('latin1')
    const pdfPages = [...pdf.matchAll(/\/Type\s*\/Page\b/g)].length
    assert.equal(pdfPages, actual.extreme.count, '打印PDF不能出现额外空白页或意外分页')
    for (const run of [actual.normal, actual.large]) {
      assert.ok(run.count > 3)
      assert.equal(run.fits, true)
      assert.deepEqual(run.materials, rows.map(row => row.materialName))
      assert.deepEqual(run.dies, rows.map((_, i) => `D${i + 1}`))
      assert.equal(run.notes, actual.expectedNotes)
      // 三列流程按每一行的列顺序输出，续页保持相同顺序。
      assert.deepEqual(run.remarks, actual.expectedRemarks)
      assert.equal(run.changes, changes)
      assert.equal(run.images, 1)
      assert.equal(run.totalImages, 2)
      assert.ok(run.structureHeading.includes('产 品 结 构 图'))
      assert.equal(run.upperFirstNumber, '1')
      const headings = ['序号', '面板作业流程', '耗时(h)', '产 品 结 构 图', '序号', '下线作业流程', '耗时(h)']
      const continuedHeadings = ['序号', '面板作业流程', '耗时(h)', '序号', '上线作业流程', '耗时(h)', '序号', '下线作业流程', '耗时(h)']
      assert.ok(run.flowHeadings.length > 0)
      assert.ok(run.flowHeadings.every(row => [JSON.stringify(headings), JSON.stringify(continuedHeadings)].includes(JSON.stringify(row))))
      assert.deepEqual([run.firstFlowRow[0], run.firstFlowRow[4]], ['1', '1'])
      assert.equal(new Set(run.flowHeaderGeometry.map(cell => cell.top)).size, 1)
      const sumWidth = cells => cells.reduce((sum, cell) => sum + cell.width, 0)
      const groupWidths = [sumWidth(run.flowHeaderGeometry.slice(0, 3)), run.flowHeaderGeometry[3].width, sumWidth(run.flowHeaderGeometry.slice(4))]
      assert.ok(Math.max(...groupWidths) - Math.min(...groupWidths) < 1)
      assert.equal(run.overviewImages, 1)
      assert.equal(run.overviewNotes, actual.expectedOverviewNotes)
      assert.deepEqual(run.overviewValues, ['自由填写', '0 mm', '80℃', '<80℃>', '', ''])
      assert.deepEqual(run.overviewFirstValues, run.overviewValues)
      assert.equal(run.overviewHeading, '凹凸条件备注')
      const shape = run.overviewGeometry
      assert.equal(shape.height, 164)
      assert.equal(shape.structureWidth, 164)
      assert.equal(shape.structureHeight, 164)
      assert.equal(shape.conditionsHeight, 164)
      assert.equal(shape.conditionsBottomBorder, '1px')
      assert.equal(shape.remarksHeight, 164)
      assert.equal(shape.labelsFit, true)
      assert.equal(shape.notesClipped, true)
      assert.ok(shape.conditionsWidth < 230)
      assert.ok(shape.remarksWidth > shape.conditionsWidth)
      assert.ok(Math.abs(shape.width - shape.structureWidth - shape.conditionsWidth - shape.remarksWidth) < 1)
      assert.ok(shape.rows.every(height => Math.abs(height - 22) < 1))
      assert.ok(run.overviewColors.every(color => color === 'rgb(21, 101, 192)'))
      assert.ok(run.colors.every(color => color === 'rgb(204, 0, 0)'))
      assert.deepEqual(run.footers, Array.from({ length: run.count }, (_, i) => `${i + 1}／${run.count}页`))
      assert.ok(run.order.lastIndexOf('grid materials') < run.order.indexOf('grid flows'))
      assert.ok(run.order.lastIndexOf('spec-overview') < run.order.indexOf('grid materials'))
      assert.ok(run.order.lastIndexOf('grid flows') < run.order.indexOf('hours'))
      assert.ok(run.order.indexOf('hours') < run.order.indexOf('issuance'))
    }
    assert.ok(actual.large.count > actual.normal.count)
    assert.deepEqual(actual.expectedRemarks, Array.from({ length: 29 }, (_, i) => groups.flatMap((group, column) => {
      const index = column === 1 ? i - 8 : i
      return index >= 0 && index < 21 ? [`${group.label}工序${index + 1}说明`] : []
    })).flat())
    t.diagnostic(`默认字号${actual.normal.count}页；放大字号${actual.large.count}页；超长单项PDF${pdfPages}页，与预览一致，全部内容及顺序一致`)
  } finally {
    // 仅清理本测试创建且仍位于仓库.tmp内的独立目录。
    assert.equal(path.dirname(path.resolve(temporary)), temporaryRoot)
    fs.rmSync(temporary, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 })
  }
})

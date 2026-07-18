const XLSX = require('xlsx')

const inputFile = 'C:\\Users\\leo\\Documents\\材料.xlsx'
const outputFile = 'C:\\Users\\leo\\Documents\\材料_排序后.xlsx'

// Read workbook
const workbook = XLSX.readFile(inputFile)
const sheetName = workbook.SheetNames[0]
const worksheet = workbook.Sheets[sheetName]

// Convert to JSON
const data = XLSX.utils.sheet_to_json(worksheet, { defval: '', header: 1 })
console.log('Total rows:', data.length)

// First row is the main header
const mainHeader = data[0]
console.log('Main header:', mainHeader)

// Second row is sub-header (日期, 机种, 材料, 供应商, 项目, 规格, 数量...)
const subHeader = data[1]
console.log('Sub header:', subHeader)

// Data starts from row 3 (index 2)
const rawData = data.slice(2)

// Column mapping based on the structure:
// Index 0: 日期, Index 1: 机种, Index 2: 材料, Index 3: 供应商, Index 4: 项目, Index 5: 规格, Index 6: 数量
// Index 7: 承办人员, Index 8: 备注

// Filter out empty rows and convert to objects
const records = rawData
  .filter((row) => row[2] && row[2].toString().trim() !== '') // Filter rows with material
  .map((row) => ({
    日期: (row[0] || '').toString().trim(),
    机种: (row[1] || '').toString().trim(),
    材料: (row[2] || '').toString().trim(),
    供应商: (row[3] || '').toString().trim(),
    项目: (row[4] || '').toString().trim(),
    规格: (row[5] || '').toString().trim(),
    数量: (row[6] || '').toString().trim(),
    承办人员: (row[7] || '').toString().trim(),
    备注: (row[8] || '').toString().trim(),
  }))

console.log('Records with material:', records.length)

// Sort by 材料, 规格, 供应商
records.sort((a, b) => {
  const cmpMaterial = a.材料.localeCompare(b.材料, 'zh-CN')
  if (cmpMaterial !== 0) return cmpMaterial

  const cmpSpec = a.规格.localeCompare(b.规格, 'zh-CN')
  if (cmpSpec !== 0) return cmpSpec

  return a.供应商.localeCompare(b.供应商, 'zh-CN')
})

// Create output data with headers
const outputData = [
  [
    '日期',
    '机种',
    '材料',
    '供应商',
    '项目',
    '规格',
    '数量',
    '承办人员',
    '备注',
  ],
  ...records.map((r) => [
    r.日期,
    r.机种,
    r.材料,
    r.供应商,
    r.项目,
    r.规格,
    r.数量,
    r.承办人员,
    r.备注,
  ]),
]

// Create new workbook
const newWorkbook = XLSX.utils.book_new()
const newWorksheet = XLSX.utils.aoa_to_sheet(outputData)

// Set column widths
newWorksheet['!cols'] = [
  { wch: 12 }, // 日期
  { wch: 14 }, // 机种
  { wch: 40 }, // 材料
  { wch: 16 }, // 供应商
  { wch: 10 }, // 项目
  { wch: 14 }, // 规格
  { wch: 10 }, // 数量
  { wch: 10 }, // 承办人员
  { wch: 30 }, // 备注
]

XLSX.utils.book_append_sheet(newWorkbook, newWorksheet, '材料登记表')

// Write to file
XLSX.writeFile(newWorkbook, outputFile)
console.log('\nDone! Sorted file saved to:', outputFile)
console.log('Total records:', records.length)

// Show first 5 sorted records
console.log('\nFirst 5 sorted records:')
for (let i = 0; i < Math.min(5, records.length); i++) {
  console.log(`${records[i].材料} | ${records[i].规格} | ${records[i].供应商}`)
}

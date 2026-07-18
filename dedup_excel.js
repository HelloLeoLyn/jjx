const XLSX = require('xlsx')

const inputFile = 'C:\\Users\\leo\\Documents\\材料_排序后.xlsx'
const outputFile = 'C:\\Users\\leo\\Documents\\材料_去重后.xlsx'

// Read workbook
const workbook = XLSX.readFile(inputFile)
const sheetName = workbook.SheetNames[0]
const worksheet = workbook.Sheets[sheetName]

// Convert to JSON (header: 1 means array of arrays)
const data = XLSX.utils.sheet_to_json(worksheet, { defval: '', header: 1 })
console.log('Total rows (including header):', data.length)

// Header row
const header = data[0]
console.log('Header:', header)

// Data rows (skip header)
const rawData = data.slice(1)

// Filter out completely empty rows
const records = rawData.filter((row) => {
  // Keep row if at least one field has value
  return row.some((cell) => cell && cell.toString().trim() !== '')
})

console.log('Total data rows:', records.length)

// Deduplicate based on: 机种, 材料, 供应商, 项目, 规格
// These are at indices: 1, 2, 3, 4, 5
const seen = new Map()
const dedupedRecords = []

for (const row of records) {
  const 机种 = (row[1] || '').toString().trim()
  const 材料 = (row[2] || '').toString().trim()
  const 供应商 = (row[3] || '').toString().trim()
  const 项目 = (row[4] || '').toString().trim()
  const 规格 = (row[5] || '').toString().trim()

  // Create a composite key
  const key = `${机种}|||${材料}|||${供应商}|||${项目}|||${规格}`

  if (!seen.has(key)) {
    seen.set(key, true)
    dedupedRecords.push(row)
  }
}

console.log('After dedup:', dedupedRecords.length)
console.log('Removed duplicates:', records.length - dedupedRecords.length)

// Create output data with header
const outputData = [header, ...dedupedRecords]

// Create new workbook
const newWorkbook = XLSX.utils.book_new()
const newWorksheet = XLSX.utils.aoa_to_sheet(outputData)

// Set column widths (same as original)
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
console.log('\nDone! Deduped file saved to:', outputFile)

// Show some stats
console.log('\n--- 去重统计 ---')
console.log('原始数据行数:', records.length)
console.log('去重后行数:', dedupedRecords.length)
console.log('去除重复行数:', records.length - dedupedRecords.length)

// Show first 5 deduped records
console.log('\n前5条去重后数据:')
for (let i = 0; i < Math.min(5, dedupedRecords.length); i++) {
  const r = dedupedRecords[i]
  console.log(`${r[1]} | ${r[2]} | ${r[3]} | ${r[4]} | ${r[5]}`)
}

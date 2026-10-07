import Foundation
var out: [String] = []
for v in 0...0x10FFFF { if let s = Unicode.Scalar(v), CharacterSet.whitespaces.contains(s) { out.append(String(v, radix: 16)) } }
print(out.joined(separator: " "))

import Foundation
for c in ["::a:b", ": :b", " ::b"] {
  let parts = c.split(separator: ":", maxSplits: 1)
  print(c, "->", parts.map { "[\($0)]" })
}
print(CharacterSet.whitespaces.contains(Unicode.Scalar(0x85)!), CharacterSet.whitespaces.contains(Unicode.Scalar(0x1680)!), CharacterSet.whitespaces.contains(Unicode.Scalar(0x200B)!), CharacterSet.whitespaces.contains(Unicode.Scalar(0x180E)!), CharacterSet.whitespaces.contains(Unicode.Scalar(0x0B)!), CharacterSet.whitespaces.contains(Unicode.Scalar(0x202F)!))

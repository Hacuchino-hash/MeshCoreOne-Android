import Foundation
func parse(_ text: String) -> (String?, String) {
  let parts = text.split(separator: ":", maxSplits: 1)
  if parts.count > 1 { return (String(parts[0]).trimmingCharacters(in: .whitespaces), String(parts[1]).trimmingCharacters(in: .whitespaces)) }
  return (nil, text)
}
for c in [":\u{301}x:y", "a:\u{301}b", "::b", "a::b", "::a:b", " ::b", "\u{3000}N\u{a0}:\u{2003}t\u{0009}", "N:\n x \n", "N\u{200B}: x", "N\u{180E}: x"] {
  let (s, t) = parse(c)
  print(s.map { $0.unicodeScalars.map { String($0.value, radix: 16) } } as Any, t.unicodeScalars.map { String($0.value, radix: 16) })
}

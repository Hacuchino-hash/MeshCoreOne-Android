// Appended to MC1Services/Sources/MC1Services/Models/NodeConfig.swift (lines 1-239, pure Foundation)
// by run.sh. Prints Foundation's real JSONEncoder/JSONDecoder behavior for the node config format so
// the Kotlin NodeConfigJson port can be checked against iOS ground truth.
typealias C = MeshCoreNodeConfig
func enc(_ c: C, pretty: Bool = true, sorted: Bool = true) -> String {
  let e = JSONEncoder()
  var f: JSONEncoder.OutputFormatting = []
  if pretty { f.insert(.prettyPrinted) }
  if sorted { f.insert(.sortedKeys) }
  e.outputFormatting = f
  return String(data: try! e.encode(c), encoding: .utf8)!
}
func dec(_ s: String) {
  do {
    let c = try JSONDecoder().decode(C.self, from: s.data(using: .utf8)!)
    print("OK   \(s.debugDescription) -> name=\(c.name.debugDescription) acks=\(c.otherSettings?.multiAcks.map { String($0) } ?? "-") freq=\(c.radioSettings.map { String($0.frequency) } ?? "-") channels=\(c.channels?.map(\.name) ?? [])")
  } catch {
    let d = String(describing: error)
    print("ERR  \(s.debugDescription) -> \(d.hasPrefix("DecodingError.dataCorrupted") ? "dataCorrupted" : String(d.prefix(60)))")
  }
}
let ios = C(
  name: "TestNode-2", publicKey: "d4f5", privateKey: "1a2b",
  radioSettings: .init(frequency: 910525, bandwidth: 62500, spreadingFactor: 7, codingRate: 5, txPower: -9),
  positionSettings: .init(latitude: "47.6062", longitude: "-122.3321"),
  otherSettings: .init(manualAddContacts: 1, advertLocationPolicy: 0),
  channels: [.init(name: "#bravo", secret: "ffeeddccbbaa99887766554433221100")],
  contacts: [
    .init(type: 3, name: "Base-W (Room)", publicKey: "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", flags: 0, latitude: "40.7128", longitude: "-74.006", lastAdvert: 1767392516, lastModified: 1767392535),
    .init(type: 2, name: "Relay/1 \"é\"", customName: "Nick", publicKey: "0102030405060708091011121314151617181920212223242526272829303132", flags: 2, latitude: "1e-05", longitude: "0.0001", lastAdvert: 0, lastModified: UInt32.max, outPath: "aabbccdd", pathHashMode: 1),
  ])
print("=== export fixture (NodeConfigTest iosExportFixture)"); print(enc(ios))
print("=== empty config"); print(enc(C()))
print("=== empty channels"); print(enc(C(channels: [])))
print("=== escapes")
print(enc(C(name: "\u{00}\u{08}\u{0C}\r\u{7F}\u{2028}\u{1F600}/")).unicodeScalars.map { $0.value < 0x20 || $0.value >= 0x7F ? "<\(String($0.value, radix: 16))>" : String($0) }.joined())
print("=== Double.description")
for d in [0.0, -0.0, 47.6062, 0.00001, -0.000015, 1.0e-7, 0.1 + 0.2, 1e16, 1e15, 12345678901234567.0, 100000.0, 0.0001] { print(d.description) }
print("=== String equality and hasPrefix")
print("composed == decomposed:", "#caf\u{E9}" == "#cafe\u{301}", " ligature:", "\u{FB01}" == "fi", " '#'+U+0301 hasPrefix #:", "#\u{301}abc".hasPrefix("#"))
print("=== decoding")
for s in [
  #"{"name":"a","name":"b"}"#, #"{"name":"a","name":null}"#, #"{"name":null,"name":"b"}"#,
  #"{"radio_settings":{"frequency":1,"bandwidth":2,"spreading_factor":7,"coding_rate":5,"tx_power":1,"frequency":9}}"#,
  #"{"channels":[{"name":"x","secret":"s"}],"channels":[{"name":"y","secret":"s"}]}"#,
  #"{"name":"a","name":"\x"}"#, #"{"name":"\x","name":"a"}"#,
  #"{"name":"a",}"#, #"{"channels":[{"name":"a","secret":"b"},]}"#, #"{,}"#, #"{"channels":[,]}"#, #"{"name":"a",,}"#,
  "{\"u\":\"a\nb\"}", #"{"u":"\x"}"#, #"{"u":01}"#, #"{"u":1.}"#, #"{"u":-}"#, #"{"u":1.2.3}"#, #"{"u":+1}"#, #"{"u":1x}"#, #"{"u":tru}"#,
  "{\"name\":\"a\nb\"}", "{\"name\":\"a\u{01}b\"}", #"{"name":"\uD83Dx"}"#, #"{"name":01}"#,
  #"{"other_settings":{"multi_acks":01}}"#, #"{"other_settings":{"multi_acks":1.5}}"#, #"{"other_settings":{"multi_acks":256}}"#,
  #"{"other_settings":{"multi_acks":1E2}}"#, #"{"other_settings":{"multi_acks":-0.0}}"#, #"{"other_settings":{"multi_acks":2.0000000000000001}}"#,
  #"{"other_settings":{"multi_acks":1.0000000000000002}}"#, #"{"other_settings":{"multi_acks":1e400}}"#,
  "\u{FEFF}{\"name\":\"a\"}", "{\"\\x\":1}", "{\"a\nb\":1}", #"{"na\u006de":"k"}"#,
] { dec(s) }

import AVFoundation
import CoreVideo
import Foundation
let path = CommandLine.arguments[1]
let url = URL(fileURLWithPath: path)
try? FileManager.default.removeItem(at: url)
let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 128, AVVideoHeightKey: 72, AVVideoCompressionPropertiesKey: [AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel]])
input.expectsMediaDataInRealTime = false
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: 128, kCVPixelBufferHeightKey as String: 72])
writer.add(input)
guard writer.startWriting() else { fatalError("writer start: \(String(describing: writer.error))") }
writer.startSession(atSourceTime: .zero)
for i in 0..<6 {
    while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.01) }
    var optional: CVPixelBuffer?
    CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &optional)
    let buffer = optional!
    CVPixelBufferLockBaseAddress(buffer, [])
    let base = CVPixelBufferGetBaseAddress(buffer)!.assumingMemoryBound(to: UInt8.self)
    let row = CVPixelBufferGetBytesPerRow(buffer)
    for y in 0..<72 { for x in 0..<128 { let p = y * row + x * 4; base[p] = 255; base[p+1] = UInt8(x + i * 10); base[p+2] = UInt8(y * 2); base[p+3] = 120 } }
    CVPixelBufferUnlockBaseAddress(buffer, [])
    guard adaptor.append(buffer, withPresentationTime: CMTime(value: Int64(i), timescale: 12)) else { fatalError("append") }
}
input.markAsFinished()
let done = DispatchSemaphore(value: 0)
writer.finishWriting { done.signal() }
guard done.wait(timeout: .now() + 30) == .success && writer.status == .completed else { fatalError("finish: \(String(describing: writer.error))") }
print("Generated own 128x72 AVC SDR fixture: \(path)")

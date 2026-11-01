package kr.personal.sealion.one.core

import java.util.UUID

/**
 * BLE GATT layout shared by the vehicle launcher (GATT server + advertiser)
 * and the phone key app (scanner + GATT client).
 *
 * Flow per command:
 *  1. phone connects and reads [NONCE] → vehicle issues a fresh single-use nonce
 *  2. phone writes a signed [CommandFrame] to [COMMAND]
 *  3. vehicle verifies the MAC, executes, and notifies the result on [STATUS]
 */
object KeyProtocol {
    val SERVICE: UUID = UUID.fromString("5a10e000-7c3e-4f6b-9d2a-31c0de5ea701")
    val NONCE: UUID = UUID.fromString("5a10e001-7c3e-4f6b-9d2a-31c0de5ea701")
    val COMMAND: UUID = UUID.fromString("5a10e002-7c3e-4f6b-9d2a-31c0de5ea701")
    val STATUS: UUID = UUID.fromString("5a10e003-7c3e-4f6b-9d2a-31c0de5ea701")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val VERSION: Byte = 1
    const val NONCE_BYTES = 16
    const val MAC_BYTES = 16
    const val NONCE_TTL_MS = 15_000L
}

enum class Command(val code: Byte, val label: String) {
    LOCK(1, "잠금"),
    UNLOCK(2, "열림"),
    TRUNK_OPEN(3, "트렁크 열기"),
    WINDOWS_CLOSE(4, "창문 닫기"),
    CLIMATE_ON(5, "공조 켜기"),
    CLIMATE_OFF(6, "공조 끄기"),
    FIND_CAR(7, "차량 찾기"),
    PING(8, "상태 확인");

    companion object {
        fun of(code: Byte): Command? = entries.firstOrNull { it.code == code }
    }
}

enum class CommandResult(val code: Byte, val label: String) {
    OK(0, "완료"),
    BAD_FRAME(1, "잘못된 요청"),
    BAD_MAC(2, "인증 실패"),
    STALE_NONCE(3, "만료된 요청"),
    REPLAYED(4, "재전송 차단"),
    REFUSED_MOVING(5, "주행 중 거부"),
    UNSUPPORTED(6, "차량 미지원"),
    VEHICLE_ERROR(7, "차량 오류");

    companion object {
        fun of(code: Byte): CommandResult = entries.firstOrNull { it.code == code } ?: VEHICLE_ERROR
    }
}

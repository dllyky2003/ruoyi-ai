package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 媒体资源元数据（对应 emergency_media_asset.metadata_json）
 * 存储图片、音频、视频、心电图、PDF、波形等文件的扩展元数据
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
public class EmergencyMediaAssetMetadataDefinition {

    // ==================== 通用元数据 ====================

    /**
     * 图片宽度（像素）
     */
    private Integer width;

    /**
     * 图片高度（像素）
     */
    private Integer height;

    /**
     * 媒体时长（秒），适用于音频/视频
     */
    @JsonProperty("duration_sec")
    private Double durationSec;

    /**
     * 帧率（fps），适用于视频
     */
    @JsonProperty("frame_rate")
    private Double frameRate;

    /**
     * 比特率（bps），适用于音频/视频
     */
    @JsonProperty("bitrate_bps")
    private Long bitrateBps;

    /**
     * 采样率（Hz），适用于音频
     */
    @JsonProperty("sample_rate_hz")
    private Integer sampleRateHz;

    /**
     * 通道数，适用于音频
     */
    @JsonProperty("channels")
    private Integer channels;

    /**
     * 颜色空间，适用于图片
     */
    @JsonProperty("color_space")
    private String colorSpace;

    /**
     * 编码格式，如 H.264、AAC、JPEG 等
     */
    @JsonProperty("codec")
    private String codec;

    /**
     * 拍摄/创建时间
     */
    @JsonProperty("captured_at")
    private String capturedAt;

    /**
     * GPS 坐标，格式: "纬度,经度"
     */
    @JsonProperty("gps_coordinate")
    private String gpsCoordinate;

    /**
     * 设备制造商
     */
    @JsonProperty("device_make")
    private String deviceMake;

    /**
     * 设备型号
     */
    @JsonProperty("device_model")
    private String deviceModel;

    /**
     * 软件版本
     */
    @JsonProperty("software_version")
    private String softwareVersion;

    /**
     * 文件压缩率
     */
    @JsonProperty("compression_ratio")
    private Double compressionRatio;

    // ==================== 特殊场景字段 ====================

    /**
     * 心电图（ECG）相关：导联数
     */
    @JsonProperty("lead_count")
    private Integer leadCount;

    /**
     * 心电图（ECG）相关：采样率
     */
    @JsonProperty("ecg_sample_rate_hz")
    private Integer ecgSampleRateHz;

    /**
     * 波形数据相关：通道数
     */
    @JsonProperty("waveform_channels")
    private Integer waveformChannels;

    /**
     * 波形数据相关：数据点数量
     */
    @JsonProperty("waveform_points")
    private Integer waveformPoints;

    /**
     * PDF 相关：页数
     */
    @JsonProperty("page_count")
    private Integer pageCount;

    /**
     * PDF 相关：是否加密
     */
    @JsonProperty("is_encrypted")
    private Boolean isEncrypted;

    /**
     * 图片相关：EXIF 数据（原始 JSON）
     */
    @JsonProperty("exif_data")
    private Map<String, Object> exifData;

    /**
     * 扩展属性（用于存储其他未定义的元数据）
     */
    @JsonProperty("extras")
    private Map<String, Object> extras;

    // ==================== 便捷查询方法 ====================

    /**
     * 判断是否为图片类型
     */
    public boolean isImage() {
        return width != null && height != null && codec != null;
    }

    /**
     * 判断是否为视频类型
     */
    public boolean isVideo() {
        return durationSec != null && frameRate != null && bitrateBps != null;
    }

    /**
     * 判断是否为音频类型
     */
    public boolean isAudio() {
        return durationSec != null && sampleRateHz != null && channels != null;
    }

    /**
     * 获取文件大小描述（根据 byte_size 计算，但此处仅作为元数据辅助）
     */
    public String getHumanReadableSize(Long bytes) {
        if (bytes == null) return "未知";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * 获取 EXIF 中的某个属性值
     */
    public Object getExifValue(String key) {
        if (exifData == null) return null;
        return exifData.get(key);
    }

    /**
     * 获取扩展属性中的某个值
     */
    public Object getExtraValue(String key) {
        if (extras == null) return null;
        return extras.get(key);
    }

    /**
     * 检查是否包含 GPS 坐标
     */
    public boolean hasGpsCoordinate() {
        return gpsCoordinate != null && !gpsCoordinate.isEmpty();
    }

    /**
     * 获取解析后的 GPS 经纬度数组（格式：纬度,经度）
     */
    public String[] parseGpsCoordinate() {
        if (!hasGpsCoordinate()) return null;
        return gpsCoordinate.split(",");
    }
}

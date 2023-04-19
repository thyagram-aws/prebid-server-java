package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.request.AdSlot30;
import org.prebid.server.bidder.huaweiads.model.request.CellInfo;
import org.prebid.server.bidder.huaweiads.model.request.Device;
import org.prebid.server.bidder.huaweiads.model.request.ExtraInfo;
import org.prebid.server.bidder.huaweiads.model.request.Format;
import org.prebid.server.bidder.huaweiads.model.request.Geo;
import org.prebid.server.bidder.huaweiads.model.request.HuaweiAdsRequest;
import org.prebid.server.bidder.huaweiads.model.request.Network;
import org.prebid.server.bidder.huaweiads.model.request.Regs;
import org.prebid.server.bidder.huaweiads.model.util.ClientTimeConverter;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataHuaweiAds;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.prebid.server.bidder.huaweiads.model.util.CountryCodeConverter.convertCountryCode;
import static org.prebid.server.bidder.huaweiads.model.util.CountryCodeConverter.getCountryCodeFromMCC;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.API_VERSION;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.DEFAULT_COUNTRY_NAME;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.DEFAULT_MODEL_NAME;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.DEFAULT_UNKNOWN_NETWORK_TYPE;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.audio;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.banner;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.interstitial;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.magazinelock;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.rewarded;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.roll;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.splash;
import static org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants.xNative;

public class HuaweiAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> HUAWEI_ADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final JacksonMapper mapper;
    private final String endpointUrl;

    public HuaweiAdsBidder(String endpointUrl, JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        HuaweiAdsRequest huaweiAdsRequest = new HuaweiAdsRequest();
        List<AdSlot30> multislot = new ArrayList<>();
        for (final Imp imp : bidRequest.getImp()) {
            ExtImpHuaweiAds extImpHuaweiAds = unmarshalExtImpHuaweiAds(imp);

            AdSlot30 adSlot30 = getReqAdslot30(extImpHuaweiAds, imp);
            multislot.add(adSlot30);
        }

        huaweiAdsRequest.setMultislot(multislot);
        huaweiAdsRequest.setClientAdRequestId(bidRequest.getId());

        getReqJson(huaweiAdsRequest, bidRequest, )
    }

    private ExtImpHuaweiAds unmarshalExtImpHuaweiAds(Imp openRTBImp) {
        ExtImpHuaweiAds huaweiAdsImpExt = parseImpExt(openRTBImp);

        if (StringUtils.isBlank(huaweiAdsImpExt.getSlotId())) {
            throw new PreBidException("ExtImpHuaweiAds: slotid is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getAdtype())) {
            throw new PreBidException("ExtImpHuaweiAds: adtype is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getPublisherId())) {
            throw new PreBidException("ExtHuaweiAds: publisherid is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getSignKey())) {
            throw new PreBidException("ExtHuaweiAds: signkey is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getKeyId())) {
            throw new PreBidException("ExtImpHuaweiAds: keyid is empty.");
        }
        return huaweiAdsImpExt;
    }

    private ExtImpHuaweiAds parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), HUAWEI_ADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Unmarshalling error: " + e.getMessage());
        }
    }

    private AdSlot30 getReqAdslot30(ExtImpHuaweiAds huaweiAdsImpExt, Imp imp) {
        int adtype = convertAdtypeStringToInteger(huaweiAdsImpExt.getAdtype().toLowerCase());
        int testStatus = huaweiAdsImpExt.getIsTestAuthorization().equals("true") ? 1 : 0;

        AdSlot30 adslot30 = new AdSlot30();
        adslot30.setSlotid(huaweiAdsImpExt.getSlotId());
        adslot30.setAdtype(adtype);
        adslot30.setTest(testStatus);

        checkAndExtractOpenrtbFormat(adslot30, adtype, huaweiAdsImpExt.getAdtype(), imp);

        return adslot30;
    }

    private static int convertAdtypeStringToInteger(String adtypeLower) {
        return switch (adtypeLower) {
            case "native" -> xNative;
            case "rewarded" -> rewarded;
            case "interstitial" -> interstitial;
            case "roll" -> roll;
            case "splash" -> splash;
            case "magazinelock" -> magazinelock;
            case "audio" -> audio;
            default -> banner;
        };
    }

    private void checkAndExtractOpenrtbFormat(AdSlot30 adslot30, int adtype, String impExtAdType, Imp imp) {
        if (nonNull(imp.getBanner())) {
            if (adtype != banner && adtype != interstitial) {
                throw new PreBidException("check openrtb format: request has banner, doesn't correspond to huawei adtype " + impExtAdType);
            }
            getBannerFormat(adslot30, imp);
        } else if (nonNull(imp.getXNative())) {
            if (adtype != xNative) {
                throw new PreBidException("check openrtb format: request has native, doesn't correspond to huawei adtype " + impExtAdType);
            }
            getNativeFormat(adslot30, imp);
        } else if (nonNull(imp.getVideo())) {
            if (adtype != banner && adtype != interstitial && adtype != rewarded && adtype != roll) {
                throw new PreBidException("check openrtb format: request has video, doesn't correspond to huawei adtype " + impExtAdType);
            }
            getVideoFormat(adslot30, adtype, imp);
        } else if (nonNull(imp.getAudio())) {
            throw new PreBidException("check openrtb format: request has audio, not currently supported");
        } else {
            throw new PreBidException("check openrtb format: please choose one of our supported type banner, native, or video");
        }
    }

    private void getBannerFormat(AdSlot30 adslot30, Imp imp) {
        if (nonNull(imp.getBanner().getW()) && nonNull(imp.getBanner().getH())) {

            adslot30.setW(imp.getBanner().getW());
            adslot30.setH(imp.getBanner().getH());
        }

        if (CollectionUtils.isNotEmpty(imp.getBanner().getFormat())) {
            List<Format> formats = imp.getBanner().getFormat()
                    .stream()
                    .filter(f -> f.getH() != 0 && f.getW() != 0)
                    .map(f -> Format.of(f.getW(), f.getH()))
                    .collect(Collectors.toList());

            adslot30.setFormat(formats);
        }
    }

    private void getNativeFormat(AdSlot30 adslot30, Imp imp) {
        if (StringUtils.isBlank(imp.getXNative().getRequest())) {
            throw new PreBidException("extract openrtb native failed: imp.Native.Request is empty");
        }

        Request nativePayload;
        try {
            nativePayload = mapper.mapper().readValue(imp.getXNative().getRequest(), Request.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Error decoding native: " + e.getMessage());
        }

        int numMainImage = 0;
        int numVideo = 0;
        int width = 0;
        int height = 0;

        for (Asset asset : nativePayload.getAssets()) {
            if (asset.getVideo() != null) {
                numVideo++;
                continue;
            }
            if (asset.getImg() != null && asset.getImg().getType() == ???){
                numMainImage++;
                if (asset.getImg().getH() != null && asset.getImg().getW() != null) {
                    width = asset.getImg().getW();
                    height = asset.getImg().getH();
                } else if (asset.getImg().getWmin() != null && asset.getImg().getHmin() != null) {
                    width = asset.getImg().getWmin();
                    height = asset.getImg().getHmin();
                }
            }
        }

        adslot30.setW(width);
        adslot30.setH(height);

        List<String> detailedCreativeTypeList = new ArrayList<>();

        if (numVideo >= 1) {
            detailedCreativeTypeList.add("903");
        } else if (numMainImage > 1) {
            detailedCreativeTypeList.add("904");
        } else if (numMainImage == 1) {
            detailedCreativeTypeList.add("901");
        } else {
            detailedCreativeTypeList.add("913");
            detailedCreativeTypeList.add("914");
        }

        adslot30.setDetailedCreativeTypeList(detailedCreativeTypeList);
    }

    private void getVideoFormat(AdSlot30 adslot30, int adtype, Imp openRTBImp) {
        adslot30.setW(openRTBImp.getVideo().getW());
        adslot30.setH(openRTBImp.getVideo().getH());

        if (adtype == roll) {
            if (openRTBImp.getVideo().getMaxduration() == 0) {
                throw new PreBidException("Extract openrtb video failed: MaxDuration is empty when huaweiads adtype is roll.");
            }
            adslot30.setTotalDuration(openRTBImp.getVideo().getMaxduration());
        }
    }

    private String getReqJson(HuaweiAdsRequest request, BidRequest openRTBRequest, ExtraInfo extraInfo) {
        request.setVersion(API_VERSION);
        String countryCode = getReqAppInfo(request, openRTBRequest, extraInfo);
        getReqDeviceInfo(request, openRTBRequest);
        getReqNetworkInfo(request, openRTBRequest);
        getReqRegsInfo(request, openRTBRequest);
        getReqGeoInfo(request, openRTBRequest);
        getReqConsentInfo(request, openRTBRequest);
        return countryCode;
    }

    private void getReqDeviceInfo(HuaweiAdsRequest request, BidRequest openRTBRequest) {
        Device device = new Device();
        Optional.ofNullable(openRTBRequest.getDevice())
                .ifPresent(deviceData -> {
                    device.setType(deviceData.getDevicetype());
                    device.setUseragent(deviceData.getUa());
                    device.setOs(deviceData.getOs());
                    device.setVersion(deviceData.getOsv());
                    device.setMaker(deviceData.getMake());
                    device.setModel(deviceData.getModel().isEmpty() ? DEFAULT_MODEL_NAME : deviceData.getModel());
                    device.setHeight(deviceData.getH());
                    device.setWidth(deviceData.getW());
                    device.setLanguage(deviceData.getLanguage());
                    device.setPxratio(deviceData.getPxratio().floatValue());
                    String countryCode = getCountryCode(openRTBRequest);
                    device.setBelongCountry(countryCode);
                    device.setLocaleCountry(countryCode);
                    device.setIp(deviceData.getIp());
                    device.setGaid(deviceData.getIfa());
                });

        getDeviceIDFromUserExt(device, openRTBRequest);

        Optional.ofNullable(openRTBRequest.getDevice())
                .map(com.iab.openrtb.request.Device::getDnt)
                .ifPresent(dnt -> {
                    if (!device.getOaid().isEmpty()) {
                        device.setIsTrackingEnabled(dnt == 1 ? "0" : "1");
                    }
                    if (!device.getGaid().isEmpty()) {
                        device.setGaidTrackingEnabled(dnt == 1 ? "0" : "1");
                    }
                });

        request.setDevice(device);
    }

    private String getCountryCode(BidRequest openRTBRequest) {
        return Stream.of(openRTBRequest)
                .filter(request -> request.getDevice() != null && request.getDevice().getGeo() != null && !request.getDevice().getGeo().getCountry().isEmpty())
                .findFirst()
                .map(request -> convertCountryCode(request.getDevice().getGeo().getCountry()))
                .orElseGet(() -> Stream.of(openRTBRequest)
                        .filter(request -> request.getUser() != null && request.getUser().getGeo() != null && !request.getUser().getGeo().getCountry().isEmpty())
                        .findFirst()
                        .map(request -> convertCountryCode(request.getUser().getGeo().getCountry()))
                        .orElseGet(() -> Stream.of(openRTBRequest)
                                .filter(request -> request.getDevice() != null && !request.getDevice().getMccmnc().isEmpty())
                                .findFirst()
                                .map(request -> getCountryCodeFromMCC(request.getDevice().getMccmnc()))
                                .orElse(DEFAULT_COUNTRY_NAME)));
    }

    private void getDeviceIDFromUserExt(Device device, BidRequest bidRequest) {
        Optional<User> userOptional = Optional.ofNullable(bidRequest.getUser());
        if (userOptional.isPresent() && nonNull(userOptional.get().getExt())) {
            ExtUserDataHuaweiAds extUserDataHuaweiAds = mapper.mapper().readValue(userOptional.get().getExt(), ExtUserDataHuaweiAds.class);
            ExtUserDataDeviceIdHuaweiAds deviceId = extUserDataHuaweiAds.getData();

            boolean isValidDeviceId = false;
            if (ArrayUtils.isEmpty(deviceId.getOaid())) {
                device.setOaid(deviceId.getOaid()[0]);
                isValidDeviceId = true;
            }
            if (ArrayUtils.isEmpty(deviceId.getGaid())) {
                device.setGaid(deviceId.getGaid()[0]);
                isValidDeviceId = true;
            }
            if (ArrayUtils.isEmpty(deviceId.getImei())) {
                device.setImei(deviceId.getImei()[0]);
                isValidDeviceId = true;
            }
            if (!isValidDeviceId) {
                throw new PreBidException("getDeviceID: Imei, Oaid, Gaid are all empty.");
            }
            if (ArrayUtils.isEmpty(deviceId.getClientTime())) {
                device.setClientTime(ClientTimeConverter.getClientTime(deviceId.getClientTime()[0]));
            }
        } else {
            if (isNull(device.getGaid()) || device.getGaid().isEmpty()) {
                throw new PreBidException("getDeviceID: openRTBRequest.User.Ext is null and device.Gaid is not specified.");
            }
        }
    }

    private void getReqNetworkInfo(HuaweiAdsRequest request, BidRequest openRTBRequest) {
        Optional.ofNullable(openRTBRequest.getDevice())
                .ifPresent(device -> {
                    Network network = new Network();
                    network.setType(nonNull(device.getConnectiontype())
                            ? device.getConnectiontype()
                            : DEFAULT_UNKNOWN_NETWORK_TYPE);

                    String[] arr = device.getMccmnc().split("-");
                    List<CellInfo> cellInfos = device.getMccmnc().isEmpty()
                            ? List.of()
                            : List.of(CellInfo.of(arr[0], arr[1]));

                    switch (arr[0] + arr[1]) {
                        case "46000", "46002", "46007" -> network.setCarrier(2);
                        case "46001", "46006" -> network.setCarrier(1);
                        case "46003", "46005", "46011" -> network.setCarrier(3);
                        default -> network.setCarrier(99);
                    }

                    network.setCellInfo(cellInfos);
                    request.setNetwork(network);
                });
    }

    private void getReqRegsInfo(HuaweiAdsRequest request, BidRequest openRTBRequest) {
        if (openRTBRequest.getRegs() != null && openRTBRequest.getRegs().getCoppa() >= 0) {
            request.setRegs(Regs.of(openRTBRequest.getRegs().getCoppa()));
        }
    }

    private void getReqGeoInfo(HuaweiAdsRequest request, BidRequest openRTBRequest) {
        if (openRTBRequest.getDevice() != null && openRTBRequest.getDevice().getGeo() != null) {
            Geo geo = Geo.of(openRTBRequest.getDevice().getGeo().getLon(),
                    openRTBRequest.getDevice().getGeo().getLat(),
                    openRTBRequest.getDevice().getGeo().getAccuracy(),
                    openRTBRequest.getDevice().getGeo().getLastfix());

            request.setGeo(geo);
        }
    }

    private void getReqConsentInfo(HuaweiAdsRequest request, BidRequest openRtbRequest) {
        if (openRtbRequest.getUser() != null && openRtbRequest.getUser().getExt() != null) {
            ExtUser extUser = openRtbRequest.getUser().getExt();

            request.setConsent(extUser.getConsent());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {

    }


}
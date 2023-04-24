package org.prebid.server.bidder.huaweiads.model.request;

import org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants;

import java.util.Optional;

public enum CountryCode {

    AND("AD"), AGO("AO"), AUT("AT"), BGD("BD"), BLR("BY"), CAF("CF"), CHD("TD"), CHL("CL"), CHN("CN"), COG("CG"),
    COD("CD"), DNK("DK"), GNQ("GQ"), EST("EE"), GIN("GN"), GNB("GW"), GUY("GY"), IRQ("IQ"), IRL("IE"), ISR("IL"),
    KAZ("KZ"), LBY("LY"), MDG("MG"), MDV("MV"), MEX("MX"), MNE("ME"), MOZ("MZ"), PAK("PK"), PNG("PG"), PRY("PY"),
    POL("PL"), PRT("PT"), SRB("RS"), SVK("SK"), SVN("SI"), SWE("SE"), TUN("TN"), TUR("TR"), TKM("TM"), UKR("UA"),
    ARE("AE"), URY("UY");

    private final String code;

    CountryCode(String code) {
        this.code = code;
    }

    public static String convertCountryCode(String country) {
        if (country == null || country.isEmpty()) {
            return HuaweiAdsConstants.DEFAULT_COUNTRY_NAME;
        }
        return Optional.of(CountryCode.valueOf(country).name())
                .orElse(country.length() >= 3 ? country.substring(0, 2) : HuaweiAdsConstants.DEFAULT_COUNTRY_NAME);
    }

    public static String getCountryCodeFromMCC(String mccValue) {
        String countryCode = Optional.ofNullable(mccValue)
                .map(mcc -> mcc.split("-")[0])
                .filter(mcc -> mcc.matches("\\d+"))
                .map(Integer::parseInt)
                .flatMap(mcc -> Optional.of(Mcc.fromCode(mcc)))
                .orElse(HuaweiAdsConstants.DEFAULT_COUNTRY_NAME);

        return countryCode.toUpperCase();
    }

    public String getCode() {
        return code;
    }
}

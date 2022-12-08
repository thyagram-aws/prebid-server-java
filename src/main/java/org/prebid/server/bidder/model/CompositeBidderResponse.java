package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.FledgeConfig;

import java.util.Collections;
import java.util.List;

/**
 * Composite bidder response (bids + other data) returned by a {@link Bidder}.
 */
@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@Value
public class CompositeBidderResponse {

    List<BidderBid> bids;

    List<BidderError> errors;

    /** FLEDGE interest group bids passback */
    List<FledgeConfig> fledgeConfigs;

    public static CompositeBidderResponse empty() {
        return new CompositeBidderResponse(Collections.emptyList(), Collections.emptyList(), null);
    }

    public static CompositeBidderResponse withBids(List<BidderBid> bids) {
        return new CompositeBidderResponse(bids, Collections.emptyList(), null);
    }

    public static CompositeBidderResponse withBids(List<BidderBid> bids, List<FledgeConfig> fledgeConfigs) {
        return new CompositeBidderResponse(bids, Collections.emptyList(), fledgeConfigs);
    }

    public static CompositeBidderResponse withError(BidderError error) {
        return new CompositeBidderResponse(Collections.emptyList(), Collections.singletonList(error), null);
    }
}
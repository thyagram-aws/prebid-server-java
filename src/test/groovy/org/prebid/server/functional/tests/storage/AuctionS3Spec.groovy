package org.prebid.server.functional.tests.storage

import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.S3Service
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST

class AuctionS3Spec extends StorageBaseSpec {

    def "PBS auction should populate imp[0].secure depend which value in imp stored request from S3 service"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        and: "Save storedImp into S3 service"
        def secureStoredRequest = PBSUtils.getRandomNumber(0, 1)
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.defaultImpression.tap {
                it.secure = secureStoredRequest
            }
        }
        S3_SERVICE.uploadStoredImp(DEFAULT_BUCKET, storedImp)

        when: "Requesting PBS auction"
        s3StoragePbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain imp[0].secure same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].secure == secureStoredRequest
    }

    def "PBS should throw exception when trying populate imp[0].secure from imp stored request on S3 service with invalid impId in file"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        and: "Save storedImp with different impId into S3 service"
        def secureStoredRequest = PBSUtils.getRandomNumber(0, 1)
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impId = PBSUtils.randomString
            impData = Imp.defaultImpression.tap {
                it.secure = secureStoredRequest
            }
        }
        S3_SERVICE.uploadStoredImp(DEFAULT_BUCKET, storedImp, storedRequestId)

        when: "Requesting PBS auction"
        s3StoragePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should throw request format error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "Invalid request format: Stored request processing failed: " +
                "No stored impression found for id: ${storedRequestId}"
    }

    def "PBS should throw exception when trying populate imp[0].secure from invalid imp stored request on S3 service"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        and: "Save storedImp into S3 service"
        S3_SERVICE.uploadFile(DEFAULT_BUCKET, INVALID_FILE_BODY, "${S3Service.DEFAULT_IMPS_DIR}/${storedRequestId}.json" )

        when: "Requesting PBS auction"
        s3StoragePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should throw request format error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "Invalid request format: Stored request processing failed: " +
                "Can't parse Json for stored request with id ${storedRequestId}"
    }

    def "PBS should throw exception when trying populate imp[0].secure from unexciting imp stored request on S3 service"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        when: "Requesting PBS auction"
        s3StoragePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should throw request format error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "Invalid request format: Stored request processing failed: " +
                "No stored impression found for id: ${storedRequestId}"
    }
}

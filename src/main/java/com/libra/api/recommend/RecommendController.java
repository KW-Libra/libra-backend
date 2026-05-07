package com.libra.api.recommend;

import com.libra.api.auth.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommend")
public class RecommendController {

    private final IndexRecommenderService recommenderService;

    public RecommendController(IndexRecommenderService recommenderService) {
        this.recommenderService = recommenderService;
    }

    @GetMapping("/index")
    public List<IndexMeta> catalog(@AuthenticationPrincipal AuthenticatedUser principal) {
        return IndexCatalog.ALL;
    }

    @PostMapping("/index")
    public List<IndexRecommendation> recommend(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody IndexRecommendationRequest request
    ) {
        return recommenderService.recommend(request);
    }

    @GetMapping("/holdings")
    public List<HoldingSample> holdings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam String indexCode,
            @RequestParam(defaultValue = "10000000") double capitalKrw
    ) {
        return recommenderService.sampleHoldings(indexCode, capitalKrw);
    }
}

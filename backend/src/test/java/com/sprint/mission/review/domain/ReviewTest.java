package com.sprint.mission.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinProfile;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewTest {

    private static final String CONTENT = "촉촉하게 잘 스며들어서 만족한다.";
    private static final List<String> IMAGES = List.of("https://img.example.com/1.jpg");

    private Review write(int rating, String content, List<String> imageUrls) {
        return Review.write(1L, 2L, 3L, rating, content, imageUrls, null, Set.of());
    }

    @Test
    void 본문은_앞뒤_공백을_떼고_저장한다() {
        Review review = write(5, "  " + CONTENT + " \n", IMAGES);

        assertThat(review.getContent()).isEqualTo(CONTENT);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getImageUrls()).containsExactlyElementsOf(IMAGES);
    }

    @Test
    void 규칙을_어기면_IllegalArgumentException() {
        assertThatThrownBy(() -> write(0, CONTENT, IMAGES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> write(6, CONTENT, IMAGES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> write(5, " 123456789 ", IMAGES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> write(5, "가".repeat(1_001), IMAGES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> write(5, CONTENT, java.util.Collections.nCopies(6, "https://img.example.com/1.jpg")))
                .isInstanceOf(IllegalArgumentException.class);

        Review review = write(5, CONTENT, IMAGES);
        assertThatThrownBy(() -> review.edit(0, CONTENT, IMAGES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> review.edit(5, "짧다", IMAGES)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수정하면_별점_차이를_돌려주고_스냅샷은_그대로다() {
        Review review = Review.write(1L, 2L, 3L, 5, CONTENT, IMAGES,
                new SkinProfile(SkinType.DRY, SkinTone.COOL), Set.of(SkinConcern.TROUBLE));

        int delta = review.edit(3, "  흡수는 빠른데 향이 아쉬웠다.  ", List.of("https://img.example.com/2.jpg"));

        assertThat(delta).isEqualTo(-2);
        assertThat(review.getRating()).isEqualTo(3);
        assertThat(review.getContent()).isEqualTo("흡수는 빠른데 향이 아쉬웠다.");
        assertThat(review.getImageUrls()).containsExactly("https://img.example.com/2.jpg");
        assertThat(review.getSkinProfile().getSkinType()).isEqualTo(SkinType.DRY);
        assertThat(review.getSkinProfile().getSkinTone()).isEqualTo(SkinTone.COOL);
        assertThat(review.getSkinConcerns()).containsExactly(SkinConcern.TROUBLE);
    }
}

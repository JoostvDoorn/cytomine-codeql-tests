package be.cytomine.service.ontology;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.TestUtils;
import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.ontology.*;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.User;
import be.cytomine.domain.security.UserJob;
import be.cytomine.dto.AnnotationLight;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.AlgoAnnotationListing;
import be.cytomine.repository.AnnotationListing;
import be.cytomine.repository.ReviewedAnnotationListing;
import be.cytomine.repository.UserAnnotationListing;
import be.cytomine.repository.ontology.UserAnnotationRepository;
import be.cytomine.service.AnnotationListingService;
import be.cytomine.service.CommandService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.dto.AnnotationResult;
import be.cytomine.service.ontology.UserAnnotationService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import org.apache.commons.lang3.time.DateUtils;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_SUPER_ADMIN", username = "superadmin")
@Transactional
public class AnnotationListingServiceTests {

    @Autowired
    UserAnnotationService userAnnotationService;

    @Autowired
    UserAnnotationRepository userAnnotationRepository;

    @Autowired
    BasicInstanceBuilder builder;

    @Autowired
    CommandService commandService;

    @Autowired
    TransactionService transactionService;

    @Autowired
    EntityManager entityManager;

    @Autowired
    AnnotationListingService annotationListingService;

    @Test
    void search_user_annotation_by_project() {
        UserAnnotation userAnnotation = builder.given_a_user_annotation();
        UserAnnotation userAnnotationFromAnotherProject = builder.given_a_user_annotation();

        UserAnnotationListing userAnnotationListing = new UserAnnotationListing(entityManager);
        userAnnotationListing.setProject(userAnnotation.getProject().getId());
        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(userAnnotation.getId())
                .doesNotContain(userAnnotationFromAnotherProject.getId());
    }

    @Test
    void search_user_annotation_by_image() {
        UserAnnotation userAnnotation = builder.given_a_user_annotation();
        UserAnnotation userAnnotationFromAnotherImage = builder.given_a_user_annotation();

        UserAnnotationListing userAnnotationListing = new UserAnnotationListing(entityManager);
        userAnnotationListing.setImage(userAnnotation.getImage().getId());
        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(userAnnotation.getId())
                .doesNotContain(userAnnotationFromAnotherImage.getId());
    }

    @Test
    void search_user_annotation_by_images() {
        UserAnnotation userAnnotation = builder.given_a_user_annotation();
        UserAnnotation userAnnotationFromAnotherImage = builder.given_a_user_annotation();
        userAnnotationFromAnotherImage.getImage().setProject(userAnnotation.getProject());

        UserAnnotationListing userAnnotationListing = new UserAnnotationListing(entityManager);
        userAnnotationListing.setImages(Arrays.asList(userAnnotation.getImage().getId(), userAnnotationFromAnotherImage.getImage().getId()));
        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(userAnnotation.getId(), userAnnotationFromAnotherImage.getId());

        userAnnotationListing.setImages(Arrays.asList(userAnnotation.getImage().getId()));
        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(userAnnotation.getId()).doesNotContain(userAnnotationFromAnotherImage.getId());
    }

    @Test
    void search_user_annotation_by_images_from_different_project_fails() {
        UserAnnotation userAnnotation = builder.given_a_user_annotation();
        UserAnnotation userAnnotationFromAnotherImage = builder.given_a_user_annotation();

        UserAnnotationListing userAnnotationListing = new UserAnnotationListing(entityManager);
        userAnnotationListing.setImages(Arrays.asList(userAnnotation.getImage().getId(), userAnnotationFromAnotherImage.getImage().getId()));
        Assertions.assertThrows(WrongArgumentException.class, () -> annotationListingService.listGeneric(userAnnotationListing));
    }

    @Test
    void search_user_annotation_by_terms() {
        UserAnnotation userAnnotation = builder.given_an_annotation_term().getUserAnnotation();
        UserAnnotation userAnnotationWithDifferentTerm
                = builder.given_a_user_annotation();
        userAnnotationWithDifferentTerm.setImage(userAnnotation.getImage());
        userAnnotationWithDifferentTerm.setProject(userAnnotation.getProject());
        builder.given_an_annotation_term(userAnnotationWithDifferentTerm, builder.given_a_term(userAnnotationWithDifferentTerm.getProject().getOntology()));

        entityManager.refresh(userAnnotation);
        entityManager.refresh(userAnnotationWithDifferentTerm);

        UserAnnotationListing userAnnotationListing = new UserAnnotationListing(entityManager);
        userAnnotationListing.setTerms(Arrays.asList(userAnnotation.getTerms().get(0).getId()));
        userAnnotationListing.setProject(userAnnotation.getProject().getId());
        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(userAnnotation.getId())
                .doesNotContain(userAnnotationWithDifferentTerm.getId());

        userAnnotationListing.setTerms(Arrays.asList(userAnnotation.getTerms().get(0).getId(), userAnnotationWithDifferentTerm.getTerms().get(0).getId()));
        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(userAnnotation.getId(), userAnnotationWithDifferentTerm.getId());
    }


    @Test
    void search_user_annotation_with_multiple_terms() {
        UserAnnotation userAnnotation = builder.given_an_annotation_term().getUserAnnotation();
        builder.given_an_annotation_term(userAnnotation, builder.given_a_term(userAnnotation.getProject().getOntology()));

        entityManager.refresh(userAnnotation);

        UserAnnotationListing userAnnotationListing = new UserAnnotationListing(entityManager);
        userAnnotationListing.setTerms(Arrays.asList(userAnnotation.getTerms().get(0).getId(), userAnnotation.getTerms().get(1).getId()));
        userAnnotationListing.setProject(userAnnotation.getProject().getId());

        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(userAnnotation.getId());
        AnnotationResult annotationResult = (AnnotationResult) annotationListingService.listGeneric(userAnnotationListing).get(0);
        assertThat(annotationResult.get("id")).isEqualTo(userAnnotation.getId());
        assertThat((List<Long>)annotationResult.get("term")).containsExactlyElementsOf(userAnnotation.getTerms().stream().map(CytomineDomain::getId).collect(Collectors.toList()));
    }



    @Test
    void search_reviewed_annotation_by_project() {
        ReviewedAnnotation reviewedAnnotation = builder.given_a_reviewed_annotation();
        ReviewedAnnotation reviewedAnnotationFromAnotherProject = builder.given_a_reviewed_annotation();

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setProject(reviewedAnnotation.getProject().getId());
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(reviewedAnnotation.getId())
                .doesNotContain(reviewedAnnotationFromAnotherProject.getId());
    }

    @Test
    void search_reviewed_annotation_by_image() {
        ReviewedAnnotation reviewedAnnotation = builder.given_a_reviewed_annotation();
        ReviewedAnnotation reviewedAnnotationFromAnotherImage = builder.given_a_reviewed_annotation();

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setImage(reviewedAnnotation.getImage().getId());
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(reviewedAnnotation.getId())
                .doesNotContain(reviewedAnnotationFromAnotherImage.getId());
    }

    @Test
    void search_reviewed_annotation_by_images() {
        ReviewedAnnotation reviewedAnnotation = builder.given_a_reviewed_annotation();
        ReviewedAnnotation reviewedAnnotationFromAnotherImage = builder.given_a_reviewed_annotation();
        reviewedAnnotationFromAnotherImage.setImage(builder.given_an_image_instance(reviewedAnnotation.getProject()));

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setImages(Arrays.asList(reviewedAnnotation.getImage().getId(), reviewedAnnotationFromAnotherImage.getImage().getId()));
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(reviewedAnnotation.getId())
                .contains(reviewedAnnotationFromAnotherImage.getId());
    }

    @Test
    void search_reviewed_annotation_by_images_from_different_project_fails() {
        ReviewedAnnotation reviewedAnnotation = builder.given_a_reviewed_annotation();
        ReviewedAnnotation reviewedAnnotationFromAnotherImage = builder.given_a_reviewed_annotation();

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setImages(Arrays.asList(reviewedAnnotation.getImage().getId(), reviewedAnnotationFromAnotherImage.getImage().getId()));
        Assertions.assertThrows(WrongArgumentException.class, () -> annotationListingService.listGeneric(reviewedAnnotationListing));
    }


    @Test
    void search_reviewed_annotation_by_users() {
        ReviewedAnnotation reviewedAnnotation = builder.given_a_reviewed_annotation();

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setImages(Arrays.asList(reviewedAnnotation.getImage().getId()));
        reviewedAnnotationListing.setUser(reviewedAnnotation.getUser().getId());
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(reviewedAnnotation.getId());

        reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setImages(Arrays.asList(reviewedAnnotation.getImage().getId()));
        reviewedAnnotationListing.setUser(builder.given_a_user().getId());
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .doesNotContain(reviewedAnnotation.getId());
    }

    static Map<String, String> POLYGONES = Map.of(
            "a", "POLYGON ((1 1, 2 1, 2 2, 1 2, 1 1))",
            "b", "POLYGON ((1 3, 2 3, 2 5, 1 5, 1 3))",
            "c", "POLYGON ((3 1, 5 1,  5 3, 3 3, 3 1))",
            "d", "POLYGON ((4 4,8 4, 8 7,4 7,4 4))",
            "e", "POLYGON ((2 2, 3 2, 3 4, 2 4, 2 2))"
    ); //e intersect a,b and c

    @Test
    void search_reviewed_annotation_by_terms() throws ParseException {

        SliceInstance sliceInstance = builder.given_a_slice_instance();
        User user1 = builder.given_a_user();
        User user2 = builder.given_a_user();

        Term term1 = builder.given_a_term(sliceInstance.getProject().getOntology());
        Term term2 = builder.given_a_term(sliceInstance.getProject().getOntology());

        ReviewedAnnotation a1 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("a"), user1, term1);
        ReviewedAnnotation a2 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("b"), user1, term2);
        ReviewedAnnotation a3 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("c"), user2, term1);
        ReviewedAnnotation a4 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("d"), user2, term2);


        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setProject(sliceInstance.getProject().getId());
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .contains(a2.getId())
                .contains(a3.getId())
                .contains(a4.getId());

        reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setProject(sliceInstance.getProject().getId());
        reviewedAnnotationListing.setTerm(term1.getId());
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .doesNotContain(a2.getId())
                .contains(a3.getId())
                .doesNotContain(a4.getId());

        reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setProject(sliceInstance.getProject().getId());
        reviewedAnnotationListing.setTerms(Arrays.asList(term1.getId(), term2.getId()));
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .contains(a2.getId())
                .contains(a3.getId())
                .contains(a4.getId());
    }


    @Test
    void search_reviewed_annotation_by_bbox() throws ParseException {

        SliceInstance sliceInstance = builder.given_a_slice_instance();
        User user1 = builder.given_a_user();
        User user2 = builder.given_a_user();

        Term term1 = builder.given_a_term(sliceInstance.getProject().getOntology());
        Term term2 = builder.given_a_term(sliceInstance.getProject().getOntology());

        ReviewedAnnotation a1 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("a"), user1, term1);
        ReviewedAnnotation a2 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("b"), user1, term2);
        ReviewedAnnotation a3 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("c"), user2, term1);
        ReviewedAnnotation a4 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("d"), user2, term2);

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setSlice(sliceInstance.getId());
        reviewedAnnotationListing.setBbox("POLYGON ((2 2, 3 2, 3 4, 2 4, 2 2))");
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .contains(a2.getId())
                .contains(a3.getId())
                .doesNotContain(a4.getId());

    }

    @Test
    void search_reviewed_annotation_by_image_and_review_user() throws ParseException {

        SliceInstance sliceInstance = builder.given_a_slice_instance();
        User user1 = builder.given_a_user();
        User user2 = builder.given_a_user();

        Term term1 = builder.given_a_term(sliceInstance.getProject().getOntology());
        Term term2 = builder.given_a_term(sliceInstance.getProject().getOntology());

        ReviewedAnnotation a1 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("a"), user1, term1);
        ReviewedAnnotation a2 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("b"), user1, term2);
        ReviewedAnnotation a3 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("c"), user2, term1);
        ReviewedAnnotation a4 = builder.given_a_reviewed_annotation(sliceInstance, POLYGONES.get("d"), user2, term2);


        a1.setReviewUser(user1);
        a2.setReviewUser(user1);
        a3.setReviewUser(user2);
        a4.setReviewUser(user2);

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setSlice(sliceInstance.getId());
        reviewedAnnotationListing.setReviewUsers(Arrays.asList(user1.getId()));
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .contains(a2.getId())
                .doesNotContain(a3.getId())
                .doesNotContain(a4.getId());


        reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setSlice(sliceInstance.getId());
        reviewedAnnotationListing.setReviewUsers(Arrays.asList(user2.getId()));
        assertThat(annotationListingService.listGeneric(reviewedAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .doesNotContain(a1.getId())
                .doesNotContain(a2.getId())
                .contains(a3.getId())
                .contains(a4.getId());
    }



    @Test
    void search_algo_annotation_by_project() {
        AlgoAnnotation algoAnnotation = builder.given_a_algo_annotation();
        AlgoAnnotation algoAnnotationFromAnotherProject = builder.given_a_algo_annotation();

        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setProject(algoAnnotation.getProject().getId());
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(algoAnnotation.getId())
                .doesNotContain(algoAnnotationFromAnotherProject.getId());
    }

    @Test
    void search_algo_annotation_by_image() {
        AlgoAnnotation algoAnnotation = builder.given_a_algo_annotation();
        AlgoAnnotation algoAnnotationFromAnotherImage = builder.given_a_algo_annotation();

        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setImage(algoAnnotation.getImage().getId());
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(algoAnnotation.getId())
                .doesNotContain(algoAnnotationFromAnotherImage.getId());
    }

    @Test
    void search_algo_annotation_by_images() {
        AlgoAnnotation algoAnnotation = builder.given_a_algo_annotation();
        AlgoAnnotation algoAnnotationFromAnotherImage = builder.given_a_algo_annotation();
        algoAnnotationFromAnotherImage.setImage(builder.given_an_image_instance(algoAnnotation.getProject()));

        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setImages(Arrays.asList(algoAnnotation.getImage().getId(), algoAnnotationFromAnotherImage.getImage().getId()));
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(algoAnnotation.getId())
                .contains(algoAnnotationFromAnotherImage.getId());
    }

    @Test
    void search_algo_annotation_by_images_from_different_project_fails() {
        AlgoAnnotation algoAnnotation = builder.given_a_algo_annotation();
        AlgoAnnotation algoAnnotationFromAnotherImage = builder.given_a_algo_annotation();

        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setImages(Arrays.asList(algoAnnotation.getImage().getId(), algoAnnotationFromAnotherImage.getImage().getId()));
        Assertions.assertThrows(WrongArgumentException.class, () -> annotationListingService.listGeneric(algoAnnotationListing));
    }


    @Test
    void search_algo_annotation_by_users() {
        AlgoAnnotation algoAnnotation = builder.given_a_algo_annotation();

        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setImages(Arrays.asList(algoAnnotation.getImage().getId()));
        algoAnnotationListing.setUser(algoAnnotation.getUser().getId());
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(algoAnnotation.getId());

        algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setImages(Arrays.asList(algoAnnotation.getImage().getId()));
        algoAnnotationListing.setUser(builder.given_a_user().getId());
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .doesNotContain(algoAnnotation.getId());
    }
    
    @Test
    void search_algo_annotation_by_terms() throws ParseException {

        SliceInstance sliceInstance = builder.given_a_slice_instance();
        UserJob user1 = builder.given_a_user_job();
        UserJob user2 = builder.given_a_user_job();

        Term term1 = builder.given_a_term(sliceInstance.getProject().getOntology());
        Term term2 = builder.given_a_term(sliceInstance.getProject().getOntology());

        AlgoAnnotation a1 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("a"), user1, term1);
        AlgoAnnotation a2 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("b"), user1, term2);
        AlgoAnnotation a3 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("c"), user2, term1);
        AlgoAnnotation a4 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("d"), user2, term2);


        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setProject(sliceInstance.getProject().getId());
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .contains(a2.getId())
                .contains(a3.getId())
                .contains(a4.getId());

        algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setProject(sliceInstance.getProject().getId());
        algoAnnotationListing.setTerm(term1.getId());
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .doesNotContain(a2.getId())
                .contains(a3.getId())
                .doesNotContain(a4.getId());

        algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setProject(sliceInstance.getProject().getId());
        algoAnnotationListing.setTerms(Arrays.asList(term1.getId(), term2.getId()));
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .contains(a2.getId())
                .contains(a3.getId())
                .contains(a4.getId());
    }


    @Test
    void search_algo_annotation_by_bbox() throws ParseException {

        SliceInstance sliceInstance = builder.given_a_slice_instance();
        UserJob user1 = builder.given_a_user_job();
        UserJob user2 = builder.given_a_user_job();


        Term term1 = builder.given_a_term(sliceInstance.getProject().getOntology());
        Term term2 = builder.given_a_term(sliceInstance.getProject().getOntology());

        AlgoAnnotation a1 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("a"), user1, term1);
        AlgoAnnotation a2 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("b"), user1, term2);
        AlgoAnnotation a3 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("c"), user2, term1);
        AlgoAnnotation a4 = builder.given_a_algo_annotation(sliceInstance, POLYGONES.get("d"), user2, term2);

        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setSlice(sliceInstance.getId());
        algoAnnotationListing.setBbox("POLYGON ((2 2, 3 2, 3 4, 2 4, 2 2))");
        assertThat(annotationListingService.listGeneric(algoAnnotationListing)
                .stream().map(x -> ((AnnotationResult) x).get("id")))
                .contains(a1.getId())
                .contains(a2.getId())
                .contains(a3.getId())
                .doesNotContain(a4.getId());

    }

    @Test
    void search_algo_annotation_with_multiple_terms() {
        AlgoAnnotation algoAnnotation = entityManager.find(AlgoAnnotation.class, builder.given_an_algo_annotation_term().getAnnotationIdent());
        AlgoAnnotationTerm secondTerm = builder.given_an_algo_annotation_term();
        secondTerm.setTerm(builder.given_a_term(algoAnnotation.getProject().getOntology()));
        secondTerm.setAnnotation(algoAnnotation);
        secondTerm.setProject(algoAnnotation.getProject());
        builder.persistAndReturn(secondTerm);
        entityManager.refresh(algoAnnotation);

        AlgoAnnotationListing userAnnotationListing = new AlgoAnnotationListing(entityManager);
        userAnnotationListing.setProject(algoAnnotation.getProject().getId());
        userAnnotationListing.setTerms(algoAnnotation.termsId());
        assertThat(annotationListingService.listGeneric(userAnnotationListing)
                .stream().map(x->((AnnotationResult)x).get("id")))
                .contains(algoAnnotation.getId());
        AnnotationResult annotationResult = (AnnotationResult) annotationListingService.listGeneric(userAnnotationListing).get(0);
        assertThat(annotationResult.get("id")).isEqualTo(algoAnnotation.getId());
        assertThat((List<Long>)annotationResult.get("term")).containsExactlyElementsOf(algoAnnotation.termsId());
    }
}

package be.cytomine.service;

import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.ForbiddenException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.ServerException;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.security.current.CurrentUser;
import be.cytomine.utils.SecurityUtils;
import be.cytomine.utils.WeakConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class CurrentUserService {

    @Autowired
    private SecUserRepository secUserRepository;


    public String getCurrentUsername() {
        CurrentUser currentUser = SecurityUtils.getSecurityCurrentUser().orElseThrow(() -> new ServerException("Cannot read current user"));
        if (currentUser.isFullObjectProvided() || currentUser.isUsernameProvided()) {
            return currentUser.getUser().getUsername();
        } else {
            throw new ObjectNotFoundException("User", "Cannot read current username. Object " + currentUser + " is not supported");
        }
    }

    public SecUser getCurrentUser() {
        CurrentUser currentUser = SecurityUtils.getSecurityCurrentUser().orElseThrow(() -> new ServerException("Cannot read current user"));
        SecUser secUser;
        if (currentUser.isFullObjectProvided()) {
            secUser = currentUser.getUser();
        } else if(currentUser.isUsernameProvided()) {
            secUser = secUserRepository.findByUsernameLikeIgnoreCase(currentUser.getUser().getUsername()).orElseThrow(() -> new ServerException("Cannot find current user with username " + currentUser.getUser().getUsername()));
        } else {
            throw new ObjectNotFoundException("User", "Cannot read current user. Object " + currentUser + " is not supported");
        }
        checkAccountStatus(secUser);
        return secUser;
    }

    private void checkAccountStatus(SecUser secUser) {
        if (secUser.getAccountExpired()) {
            throw new ForbiddenException("Account expired");
        } else if (secUser.getAccountLocked()) {
            throw new ForbiddenException("Account locked");
        } else if (!secUser.getEnabled()) {
            throw new ForbiddenException("Account disabled");
        }

    }

}

package td.teladoum.baobab.models;

import td.teladoum.baobab.R;

import td.teladoum.baobab.models.*;
import td.teladoum.baobab.adapters.*;
import td.teladoum.baobab.repositories.*;
import td.teladoum.baobab.services.*;
import td.teladoum.baobab.firebase.*;
import td.teladoum.baobab.helpers.*;
import td.teladoum.baobab.notifications.*;
import td.teladoum.baobab.utils.*;
import td.teladoum.baobab.activities.*;

/**
 * Modèle représentant un utilisateur de l'application BaobabTD.
 * Contient les identifiants, les informations de profil, les paramètres de confidentialité
 * et le statut de présence en ligne (isOnline, lastSeen).
 */
public class User {

    private int id;
    private String name;
    private String firstName;
    private String lastName;
    private String phone;
    private String profession;
    private String relationshipStatus;
    private String gender;
    private String email;
    private String password;
    private String profileImage;
    private String dob;
    private String neighborhood;
    private String city;
    private String country;

    private boolean hideEmail;
    private boolean hideDob;
    private boolean hideLocation;

    private boolean isOnline;
    private String lastSeen;

    private boolean isSuspended;
    private String shareLimitedUntil;
    private String suspensionReason;

    private boolean isAdmin;
    private String bio;

    public User() {
    }

    public User(int id,
                String name,
                String email) {

        this.id = id;
        this.name = name;
        this.email = email;
    }

    public User(int id,
                String name,
                String email,
                String profileImage) {

        this.id = id;
        this.name = name;
        this.email = email;
        this.profileImage = profileImage;
    }

    public User(int id,
                String name,
                String phone,
                String email,
                String password,
                String profileImage) {

        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.profileImage = profileImage;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        if ((name == null || name.trim().isEmpty()) && (lastName != null || firstName != null)) {
            return ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public String getProfile_image() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public void setProfile_image(String profile_image) {
        if (profile_image != null && !profile_image.trim().isEmpty()) {
            this.profileImage = profile_image;
        }
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(String relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public boolean isHideEmail() {
        return hideEmail;
    }

    public void setHideEmail(boolean hideEmail) {
        this.hideEmail = hideEmail;
    }

    public boolean isPrivateProfile() {
        return hideEmail && hideDob && hideLocation;
    }

    public void setPrivateProfile(boolean privateProfile) {
        this.hideEmail = privateProfile;
        this.hideDob = privateProfile;
        this.hideLocation = privateProfile;
    }

    public String getDisplayEmail(int viewerUserId) {
        if (viewerUserId != this.id && (hideEmail || isPrivateProfile())) {
            return "********";
        }
        return email != null ? email : "";
    }

    public String getDisplayDob(int viewerUserId) {
        if (viewerUserId != this.id && (hideDob || isPrivateProfile())) {
            return "********";
        }
        return dob != null ? dob : "";
    }

    public String getDisplayLocation(int viewerUserId) {
        if (viewerUserId != this.id && (hideLocation || isPrivateProfile())) {
            return "********";
        }
        String loc = ((neighborhood != null ? neighborhood : "") + " " + (city != null ? city : "") + " " + (country != null ? country : "")).trim();
        return loc.isEmpty() ? "********" : loc;
    }

    public String getDisplayPhone(int viewerUserId) {
        if (viewerUserId != this.id && (hideEmail || isPrivateProfile())) {
            return "********";
        }
        return phone != null ? phone : "";
    }

    public boolean isHideDob() {
        return hideDob;
    }

    public void setHideDob(boolean hideDob) {
        this.hideDob = hideDob;
    }

    public boolean isHideLocation() {
        return hideLocation;
    }

    public void setHideLocation(boolean hideLocation) {
        this.hideLocation = hideLocation;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isSuspended() {
        return isSuspended;
    }

    public void setSuspended(boolean suspended) {
        isSuspended = suspended;
    }

    public String getShareLimitedUntil() {
        return shareLimitedUntil;
    }

    public void setShareLimitedUntil(String shareLimitedUntil) {
        this.shareLimitedUntil = shareLimitedUntil;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public void setSuspensionReason(String suspensionReason) {
        this.suspensionReason = suspensionReason;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}


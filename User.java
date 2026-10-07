package td.teladoumbaobabtd;

public class User {

    private int id;
    private String name;
    private String firstName;
    private String lastName;
    private String phone;
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

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
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

    public boolean isHideEmail() {
        return hideEmail;
    }

    public void setHideEmail(boolean hideEmail) {
        this.hideEmail = hideEmail;
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
}

# JServ

**JServ** is a Java-based service designed to work exclusively with `midircd` and `snircd`. It combines two core modules—SpamScan and HostServ—into a single, efficient package.

## Features

- **SpamScan**  
  Detects and removes spammers from your IRC network. Includes a badword management system where operators (Opers) can add, list, or delete badwords used for spam detection. Offers commands for help, version info, and channel management.

- **HostServ**  
  Manages and stores hidden hosts for authenticated users. Users can change their hidden host once per week. Opers can manually set hidden hosts for any user.

## Compatibility

- **IRC Daemons:** Requires `midircd` and `snircd`
- **Java Runtime:** JRE 17 or higher
- **Database:** PostgreSQL

## Installation

1. **Build from Source:**  
   It is recommended to use an IDE with Maven support to compile the sources.

2. **Precompiled Release:**  
   A precompiled release is available [here](https://github.com/user-attachments/files/21146940/JServ.zip).
   - Make sure you have JRE 17 or higher and PostgreSQL installed.

## Usage

To run JServ, execute:
```sh
java -jar JServ.jar &
exit
```
This will start the service in the background.

## Notes

- The service is only functional when used alongside `midircd` and `snircd`.
- Operator commands allow manual management of user hidden hosts and badword filtering.
- Badwords for spam detection are managed via JSON config files.
- The system supports creation and maintenance of config files if they do not exist.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

**Author:** Andreas Pschorn (WarPigs)  
**Vendor:** MidiAndMore.Net  
**Version:** 1.0

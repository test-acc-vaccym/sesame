Sesame
======

Sesame is yet another Android password keyring. Unlike many implementations, Sesame is both open source and moderately secure.

[Play Store listing](https://play.google.com/store/apps/details?id=net.af0.sesame)

# Why?

Many Android password managers are [insecure](https://media.blackhat.com/bh-eu-12/Belenko/bh-eu-12-Belenko-Password_Encryption-Slides.pdf). They implement their own encryption incorrectly, or screw up [password-based key derivation](http://nelenkov.blogspot.ch/2012/04/using-password-based-encryption-on.html), or they require all kinds of permissions in order to support their subscription-only Internet backup service.

Perhaps the best one out there, from a brief survey, is Zetetic's [STRIP](https://play.google.com/store/apps/details?id=net.zetetic.strip). 

These guys develop the awesome (and open source) SQLCipher library, which is used in Sesame, but STRIP itself uses a relatively small number of key derivation rounds before encrypting the database, and demands some additional app permissions in order to provide useful features like database export. 

In contrast, Sesame uses a rather ungainly number of PBKDF rounds--which means performance on older devices is truly horrid--and demands no special permissions. 

On the other hand, I owe a rather large debt of gratitude to Zetetic for developing SQLCipher, so if you like Sesame, go buy STRIP. 

# How?

Sesame implements no encryption itself. Storage is provided by [SQLCipher](http://sqlcipher.net/design), which seems to mostly do the right thing in all the usual places. Sesame is just a thin layer on top of that. 

In brief, SQLCipher provides encrypted SQLite databases, with the encryption provided by OpenSSL. Databases are encrypted with 256-bit AES; key derivation is PBKDF2 with SHA1 as the hash. Sesameuses 256,000 rounds, which is rather slow on older devices.

Note that I have not actually reviewed the SQLCipher code. I trust that these guys mostly did things correctly, and if they did not I wouldn't necessarily spot it. ;) 

# Extra features

In addition, Sesame provides automatic cloud backup to the Android servers (but you can turn this off if you like) and manual database import/export (using Android Intents, so no additional file permissions are required). 

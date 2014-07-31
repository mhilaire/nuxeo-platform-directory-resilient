nuxeo-directory-connector
=========================

## What is this project ?

Nuxeo Resilient Directory is a simple Addon for Nuxeo Platforms that allows to set-up a resilient directory ahead of standard sub directories such as LDAP or SQL directories

The aim is to provide a fallback behavior when the master subdirectory falls.

## Why would you use this ?

You should consider this as a sample code that you can use as a guide to implement a new type of Directory on top of a custom service provider.

Typical use case is to wrapp a remote WebService as a Nuxeo Directory.

Usaing a directory to wrap a WebService provides some direct benefits :

 - ability to use a XSD schema to define the structure of the entities your expose 

      - entries are exposed as DocumentModels
      - you can then use Nuxeo Layout system to define display 
      - you can then use Nuxeo Studio to do this configuration

 - ability to reuse existing Directory features

      - Field Mapping
      - Entries caching
      - Widgets to search / select an entry

## History

This code was initially written against a Nuxeo 5.9 to be able to resuse a custom WebService as user provider.



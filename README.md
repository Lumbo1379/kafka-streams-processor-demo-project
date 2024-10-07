# Requirements

## Install just

`just` is used to run commands against the development environment

`brew install just`
`just`

## Run the development environment

`just up-kafka`

Wait until Kafka starts up. You should be able to access the Confluent Control Centre
at http://localhost:9021/ when it has.

`just start-datagen`

The above will start the data generators to simulate the event streams.

`just up`

You should see results being printed to console, and appearing in the sink topic after ~30 seconds.

## Making changes

After making changes run

`just build`

## Cleanup

`just destroy`

# Presentation

https://docs.google.com/presentation/d/1F0iEwQDS2kcTUxOLLDeYv_qtsIqn6DzBaNFW8TZAPsA/edit?usp=sharing

# Script

https://docs.google.com/document/d/1_lbhmI-qjdVfpT-2Vx4rKz5v9W-i-GFJh3aFjQNveMg/edit?usp=sharing
